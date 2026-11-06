package ru.fermocontrol.fermocontrol

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.fermocontrol.fermocontrol.data.BatchRepository
import ru.fermocontrol.fermocontrol.data.PhotoStore
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.NewBatchDraft
import ru.fermocontrol.fermocontrol.model.WeightLog
import ru.fermocontrol.fermocontrol.ui.BatchDetailSheet
import ru.fermocontrol.fermocontrol.ui.MainScreen
import ru.fermocontrol.fermocontrol.ui.NewBatchDialog
import ru.fermocontrol.fermocontrol.ui.WeighSheet
import ru.fermocontrol.fermocontrol.ui.theme.FermocontrolTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val journal = ViewModelProvider(this)[JournalViewModel::class.java]

        setContent {
            FermocontrolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        journal.loading -> JournalStatus(
                            loading = true,
                            message = "Открываем журнал…",
                            onRetry = journal::reload,
                        )

                        journal.loadError != null -> JournalStatus(
                            loading = false,
                            message = journal.loadError.orEmpty(),
                            onRetry = journal::reload,
                        )

                        else -> MainScreen(
                            batches = journal.batches,
                            onSelect = journal::openDetail,
                            onWeigh = journal::openWeigh,
                            onAdd = journal::openNewBatch,
                            onSettings = { pending("Настройки") },
                            onProfile = { pending("Профиль") },
                        )
                    }

                    if (!journal.loading && journal.loadError == null) {
                        val detailBatch = journal.detailBatch
                        if (detailBatch != null) {
                            BatchDetailSheet(
                                batch = detailBatch,
                                saving = journal.saving,
                                error = journal.archiveError,
                                photoFile = journal.photos::file,
                                onDismiss = journal::closeDetail,
                                onWeigh = { journal.openWeigh(detailBatch) },
                                onSetArchived = { archived -> journal.setArchived(archived) },
                            )
                        }

                        if (journal.newBatchOpen) {
                            NewBatchDialog(
                                saving = journal.saving,
                                error = journal.saveError,
                                onDismiss = journal::dismissNewBatch,
                                onSave = journal::addBatch,
                            )
                        }

                        val weighingBatch = journal.weighingBatch
                        if (weighingBatch != null) {
                            WeighSheet(
                                batch = weighingBatch,
                                saving = journal.saving,
                                error = journal.weighError,
								photoStore = journal.photos,
                                onDismiss = journal::dismissWeigh,
                                onSave = journal::saveWeight,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun pending(feature: String) {
        Toast.makeText(
            this,
            "$feature — следующий этап.",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

class JournalViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository = BatchRepository(application)
	    val photos = PhotoStore(application)

    // Область переживает поворот экрана вместе с ViewModel.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    var batches by mutableStateOf<List<Batch>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    var saving by mutableStateOf(false)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    var saveError by mutableStateOf<String?>(null)
        private set

    var weighError by mutableStateOf<String?>(null)
        private set

    var archiveError by mutableStateOf<String?>(null)
        private set

    var newBatchOpen by mutableStateOf(false)
        private set

    // Храним идентификатор, а актуальную партию берём из batches.
    private var weighingBatchId by mutableStateOf<Long?>(null)

    val weighingBatch: Batch?
        get() = batches.firstOrNull { it.id == weighingBatchId }

    // Открытая карточка партии.
    private var detailBatchId by mutableStateOf<Long?>(null)

    val detailBatch: Batch?
        get() = batches.firstOrNull { it.id == detailBatchId }

    init {
        reload()
    }

    fun reload() {
        if (loading || saving) return

        loading = true
        loadError = null

        scope.launch {
            try {
                batches = repository.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Fermocontrol", "Cannot load journal", error)

                loadError =
                    "Не удалось открыть журнал: " +
                    (error.localizedMessage ?: "ошибка чтения") +
                    "\n\nИсходный файл не перезаписан."
            } finally {
                loading = false
            }
        }
    }

    fun openNewBatch() {
        if (
            loading ||
            saving ||
            loadError != null ||
            weighingBatchId != null
        ) {
            return
        }

        saveError = null
        newBatchOpen = true
    }

    fun dismissNewBatch() {
        if (saving) return

        newBatchOpen = false
        saveError = null
    }

    fun addBatch(draft: NewBatchDraft) {
        if (
            loading ||
            saving ||
            loadError != null ||
            !newBatchOpen
        ) {
            return
        }

        // Устанавливаем до запуска корутины, чтобы исключить
        // повторное сохранение при двойном нажатии.
        saving = true
        saveError = null

        scope.launch {
            try {
                // Учитываем все партии, включая архивные.
                val maxId = batches.maxOfOrNull { it.id } ?: 0L
                val maxNumber = batches.maxOfOrNull { it.number } ?: 0

                require(maxId < Long.MAX_VALUE) {
                    "Закончился диапазон идентификаторов"
                }

                require(maxNumber < Int.MAX_VALUE) {
                    "Закончился диапазон номеров партий"
                }

                val now = System.currentTimeMillis()

                val batch = Batch(
                    id = maxOf(now, maxId + 1L),
                    number = maxNumber + 1,
                    name = draft.name,
                    category = draft.category,
                    rawMaterial = draft.rawMaterial,
                    spices = draft.spices,
                    casing = draft.casing,
                    startWeightGrams = draft.startWeightGrams,
                    targetLossPercent = draft.targetLossPercent,
                    temperatureC = draft.temperatureC,
                    humidityPercent = draft.humidityPercent,
                    note = draft.note,
                    createdAtEpochMs = now,
                )

                val updated = listOf(batch) + batches

                // Сначала записываем весь журнал на диск.
                repository.save(updated)

                // Обновляем экран только после успешной записи.
                batches = updated
                newBatchOpen = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Fermocontrol", "Cannot save journal", error)

                saveError =
                    "Не удалось сохранить: " +
                    (error.localizedMessage ?: "ошибка записи") +
                    "\nФорма оставлена открытой."
            } finally {
                saving = false
            }
        }
    }

    fun openWeigh(batch: Batch) {
        if (
            loading ||
            saving ||
            loadError != null ||
            newBatchOpen ||
            weighingBatchId != null
        ) {
            return
        }

        val actualBatch = batches.firstOrNull {
            it.id == batch.id
        } ?: return

        if (actualBatch.archived) return

        weighError = null
        weighingBatchId = actualBatch.id
    }

    fun dismissWeigh() {
        if (saving) return

        weighingBatchId = null
        weighError = null
    }

    fun saveWeight(
        weightGrams: Double,
        smell: String,
        surface: String,
        needsCheck: Boolean,
        photoUri: Uri?,
    ) {
        if (loading || saving || loadError != null) return

        val batch = weighingBatch ?: return

        if (batch.archived) {
            weighError = "Нельзя добавить взвешивание в архивную партию."
            return
        }

        if (!weightGrams.isFinite() || weightGrams <= 0.0) {
            weighError = "Введите вес больше нуля."
            return
        }

        saving = true
        weighError = null

        scope.launch {
            // Имя файла, который ещё не принят журналом.
            var storedPhoto: String? = null

            try {
                storedPhoto = photoUri?.let { photos.saveFrom(it) }

                val log = WeightLog(
                    atEpochMs = System.currentTimeMillis(),
                    weightGrams = weightGrams,
                    smellNote = smell,
                    surfaceNote = surface,
                    needsCheck = needsCheck,
                    photoFileName = storedPhoto,
                )

                // Добавляем запись только выбранной партии.
                val updated = batches.map { existing ->
                    if (existing.id == batch.id) {
                        existing.copy(logs = existing.logs + log)
                    } else {
                        existing
                    }
                }

                // Передаём репозиторию весь обновлённый журнал.
                repository.save(updated)

                // Закрываем форму только после успешной записи.
                batches = updated
                weighingBatchId = null

                // Теперь файл принадлежит журналу.
                storedPhoto = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Fermocontrol", "Cannot save weighing", error)

                weighError =
                    "Не удалось сохранить взвешивание: " +
                    (error.localizedMessage ?: "ошибка записи") +
                    "\nФорма оставлена открытой."
            } finally {
                // Журнал запись не принял, поэтому убираем файл.
                storedPhoto?.let(photos::delete)
                saving = false
            }
        }
    }

    fun openDetail(batch: Batch) {
        if (
            loading ||
            saving ||
            loadError != null ||
            newBatchOpen
        ) {
            return
        }

        val actual = batches.firstOrNull { it.id == batch.id } ?: return

        archiveError = null
        detailBatchId = actual.id
    }

    fun closeDetail() {
        if (saving) return

        detailBatchId = null
        archiveError = null
    }

    fun setArchived(archived: Boolean) {
        if (loading || saving || loadError != null) return

        val batch = detailBatch ?: return
        if (batch.archived == archived) return

        saving = true
        archiveError = null

        scope.launch {
            try {
                val updated = batches.map { existing ->
                    if (existing.id == batch.id) {
                        existing.copy(archived = archived)
                    } else {
                        existing
                    }
                }

                // Сначала запись на диск, потом обновление экрана.
                repository.save(updated)
                batches = updated
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Fermocontrol", "Cannot save archive state", error)

                archiveError =
                    "Не удалось изменить архив: " +
                    (error.localizedMessage ?: "ошибка записи")
            } finally {
                saving = false
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

@Composable
private fun JournalStatus(
    loading: Boolean,
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                CircularProgressIndicator()
            }

            Text(
                text = message,
                textAlign = TextAlign.Center,
            )

            if (!loading) {
                Button(onClick = onRetry) {
                    Text("Повторить")
                }
            }
        }
    }
}