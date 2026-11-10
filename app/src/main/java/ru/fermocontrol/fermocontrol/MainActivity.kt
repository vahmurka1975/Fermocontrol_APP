package ru.fermocontrol.fermocontrol

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fermocontrol.fermocontrol.data.BatchRepository
import ru.fermocontrol.fermocontrol.data.PhotoStore
import ru.fermocontrol.fermocontrol.data.SettingsStore
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.NewBatchDraft
import ru.fermocontrol.fermocontrol.model.WeightLog
import ru.fermocontrol.fermocontrol.ui.BatchDetailSheet
import ru.fermocontrol.fermocontrol.ui.CloudProvider
import ru.fermocontrol.fermocontrol.ui.MainScreen
import ru.fermocontrol.fermocontrol.ui.NewBatchDialog
import ru.fermocontrol.fermocontrol.ui.SettingsActions
import ru.fermocontrol.fermocontrol.ui.SettingsSheet
import ru.fermocontrol.fermocontrol.ui.SettingsUiState
import ru.fermocontrol.fermocontrol.ui.WeighSheet
import ru.fermocontrol.fermocontrol.ui.theme.AppPalettes
import ru.fermocontrol.fermocontrol.ui.theme.FermocontrolTheme
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.fermocontrol.ui.theme.paletteById
import ru.fermocontrol.fermocontrol.ui.rememberBackupActions
import ru.fermocontrol.fermocontrol.data.BackupManager


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val journal = ViewModelProvider(this)[JournalViewModel::class.java]

        setContent {
    val context = LocalContext.current
    val backup = rememberBackupActions(
        manager = journal.backupManager,
        onMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
        onRestored = { journal.reload() },
    )
    val palette = paletteById(journal.paletteId)

            FermocontrolTheme(palette = palette) {
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
                            onSettings = journal::openSettings,
                            onProfile = { pending("Профиль") },
                        )
                    }

                    if (!journal.loading && journal.loadError == null) {
                        val detailBatch = journal.detailBatch
                        if (detailBatch != null) {
                            BatchDetailSheet(
                                batch = detailBatch,
                                saving = journal.saving,
                                error = journal.actionError,
                                photoFile = journal.photos::file,
                                onDismiss = journal::closeDetail,
                                onWeigh = { journal.openWeigh(detailBatch) },
                                onSetArchived = { archived -> journal.setArchived(archived) },
                                onEdit = journal::openEdit,
                                onDeleteLog = journal::deleteLog,
                                onDeleteBatch = journal::deleteBatch,
                            )

                            if (journal.editOpen) {
                                NewBatchDialog(
                                    saving = journal.saving,
                                    error = journal.editError,
                                    onDismiss = journal::dismissEdit,
                                    onSave = journal::saveEdit,
                                    initial = detailBatch,
                                )
                            }
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

                        if (journal.settingsOpen) {
                            SettingsHost(
                                state = SettingsUiState(
                                    cloud = journal.cloud,
                                    activeCount = journal.batches.count { !it.archived },
                                    archiveCount = journal.batches.count { it.archived },
                                    logCount = journal.batches.sumOf { it.logs.size },
                                    storageStatus = "Журнал хранится в памяти приложения.",
                                    updateStatus = journal.updateStatus,
                                    versionTag = versionTag(),
                                    interval = journal.interval,
                                    pace = journal.pace,
                                    paletteId = journal.paletteId,
                                ),
                                actions = SettingsActions(
                                    onClose = journal::closeSettings,
                                    onCloudChange = journal::changeCloud,
                                    onConnectCloud = { pending("Подключение облака") },
                                    onExport = { backup.saveBackup() },
                                    onImport = { backup.restoreBackup() },
                                    onShare = { backup.shareBackup() },
                                    onCsv = { backup.saveCsv() },
                                    onCheckUpdates = journal::checkUpdates,
                                    onIntervalChange = journal::changeInterval,
                                    onPaceChange = journal::changePace,
                                    onPaletteChange = journal::changePalette,
                                    onGuide = { pending("Как читать показатели") },
                                    onResetDemo = { pending("Демо-данные") },
                                ),
                                onDismiss = journal::closeSettings,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun versionTag(): String {
        val name = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
        return "v" + (name ?: "?")
    }

    private fun pending(feature: String) {
        Toast.makeText(
            this,
            "$feature — следующий этап.",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHost(
    state: SettingsUiState,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    val p = LocalAppPalette.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.sheet,
        shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(33.dp, 4.dp)
                    .clip(CircleShape)
                    .background(p.chipLine),
            )
        },
    ) {
        SettingsSheet(state, actions)
    }
}

class JournalViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository = BatchRepository(application)
    val photos = PhotoStore(application)
    private val settingsStore = SettingsStore(application)
	val backupManager = BackupManager(application, repository, photos)

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

    // Ошибки действий на карточке: архив, удаление записи, удаление партии.
    var actionError by mutableStateOf<String?>(null)
        private set

    // Ошибка формы редактирования паспорта.
    var editError by mutableStateOf<String?>(null)
        private set

    var newBatchOpen by mutableStateOf(false)
        private set

    var editOpen by mutableStateOf(false)
        private set

    // ───────────── Состояние настроек ─────────────

    var settingsOpen by mutableStateOf(false)
        private set

    var paletteId by mutableStateOf(settingsStore.paletteId)
        private set

    var cloud by mutableStateOf(
        runCatching { CloudProvider.valueOf(settingsStore.cloud) }
            .getOrDefault(CloudProvider.Yandex)
    )
        private set

    var interval by mutableStateOf(settingsStore.interval)
        private set

    var pace by mutableStateOf(settingsStore.pace)
        private set

    var updateStatus by mutableStateOf(
        "Сервер обновлений и установка Android-сборок не подключены."
    )
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

    // ───────────── Общая запись журнала ─────────────

    /**
     * Сначала записывает весь журнал на диск и только потом обновляет экран.
     * Файлы фотографий удаляются после успешной записи, их сбой не считается
     * ошибкой сохранения.
     */
    private fun commit(
        updated: List<Batch>,
        failText: String,
        setError: (String?) -> Unit,
        onSaved: () -> Unit = {},
        afterSave: suspend () -> Unit = {},
    ) {
        // Устанавливаем до запуска корутины, чтобы исключить повторное нажатие.
        saving = true
        setError(null)

        scope.launch {
            try {
                repository.save(updated)

                batches = updated
                onSaved()

                try {
                    afterSave()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("Fermocontrol", "Cannot clean up files", error)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Fermocontrol", failText, error)

                setError(
                    failText + ": " +
                        (error.localizedMessage ?: "ошибка записи")
                )
            } finally {
                saving = false
            }
        }
    }

    private suspend fun removePhotos(names: List<String>) {
        if (names.isEmpty()) return

        withContext(Dispatchers.IO) {
            names.forEach { name ->
                try {
                    photos.delete(name)
                } catch (error: Exception) {
                    Log.w("Fermocontrol", "Cannot delete photo $name", error)
                }
            }
        }
    }

    // ───────────── Новая партия ─────────────

    fun openNewBatch() {
        if (
            loading ||
            saving ||
            loadError != null ||
            weighingBatchId != null ||
            editOpen
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
                    productTypeId = draft.productTypeId,
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

    // ───────────── Взвешивание ─────────────

    fun openWeigh(batch: Batch) {
        if (
            loading ||
            saving ||
            loadError != null ||
            newBatchOpen ||
            editOpen ||
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
                storedPhoto = photoUri?.let { uri ->
                    withContext(Dispatchers.IO) { photos.saveFrom(uri) }
                }

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
                saving = false
                // Журнал запись не принял, поэтому убираем файл.
                storedPhoto?.let(photos::delete)
            }
        }
    }

    // ───────────── Карточка партии ─────────────

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

        actionError = null
        detailBatchId = actual.id
    }

    fun closeDetail() {
        if (saving) return

        detailBatchId = null
        editOpen = false
        actionError = null
        editError = null
    }

    fun setArchived(archived: Boolean) {
        if (loading || saving || loadError != null) return

        val batch = detailBatch ?: return
        if (batch.archived == archived) return

        commit(
            updated = batches.map {
                if (it.id == batch.id) it.copy(archived = archived) else it
            },
            failText = "Не удалось изменить архив",
            setError = { actionError = it },
        )
    }

    // ───────────── Редактирование паспорта ─────────────

    fun openEdit() {
        if (
            loading ||
            saving ||
            loadError != null ||
            newBatchOpen ||
            weighingBatchId != null ||
            detailBatch == null
        ) {
            return
        }

        editError = null
        editOpen = true
    }

    fun dismissEdit() {
        if (saving) return

        editOpen = false
        editError = null
    }

    fun saveEdit(draft: NewBatchDraft) {
        if (loading || saving || loadError != null || !editOpen) return

        val batch = detailBatch ?: return

        if (draft.name.isBlank()) {
            editError = "Укажите название продукта."
            return
        }

        // Стартовый вес меняется только до первого взвешивания:
        // на нём построены усушка и график.
        val startWeight = if (batch.logs.isEmpty()) {
            draft.startWeightGrams
        } else {
            batch.startWeightGrams
        }

        if (!startWeight.isFinite() || startWeight <= 0.0) {
            editError = "Введите стартовый вес больше нуля."
            return
        }

        val edited = batch.copy(
            name = draft.name,
            category = draft.category,
            rawMaterial = draft.rawMaterial,
            spices = draft.spices,
            casing = draft.casing,
            productTypeId = draft.productTypeId,
            startWeightGrams = startWeight,
            targetLossPercent = draft.targetLossPercent,
            temperatureC = draft.temperatureC,
            humidityPercent = draft.humidityPercent,
            note = draft.note,
        )

        commit(
            updated = batches.map { if (it.id == batch.id) edited else it },
            failText = "Не удалось сохранить изменения",
            setError = { editError = it },
            onSaved = { editOpen = false },
        )
    }

    // ───────────── Удаление ─────────────

    fun deleteLog(log: WeightLog) {
        if (loading || saving || loadError != null) return

        val batch = detailBatch ?: return
        val index = batch.logs.indexOf(log)
        if (index < 0) return

        commit(
            updated = batches.map {
                if (it.id == batch.id) {
                    it.copy(logs = it.logs.filterIndexed { i, _ -> i != index })
                } else {
                    it
                }
            },
            failText = "Не удалось удалить запись",
            setError = { actionError = it },
            afterSave = { removePhotos(listOfNotNull(log.photoFileName)) },
        )
    }

    fun deleteBatch() {
        if (loading || saving || loadError != null) return

        val batch = detailBatch ?: return

        if (!batch.archived) {
            actionError = "Удалить можно только архивную партию."
            return
        }

        val files = listOfNotNull(batch.photoFileName) +
            batch.logs.mapNotNull { it.photoFileName }

        commit(
            updated = batches.filter { it.id != batch.id },
            failText = "Не удалось удалить партию",
            setError = { actionError = it },
            onSaved = {
                detailBatchId = null
                editOpen = false
            },
            afterSave = { removePhotos(files) },
        )
    }

    // ───────────── Настройки ─────────────

    fun openSettings() {
        if (
            loading || saving || loadError != null ||
            newBatchOpen || weighingBatchId != null || detailBatchId != null
        ) {
            return
        }

        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun changePalette(id: String) {
        // Неизвестный id в настройки не записываем.
        if (AppPalettes.none { it.id == id }) return

        paletteId = id
        settingsStore.paletteId = id
    }

    fun changeCloud(provider: CloudProvider) {
        cloud = provider
        settingsStore.cloud = provider.name
    }

    fun changeInterval(days: Int) {
        if (days !in 1..30) return
        interval = days
        settingsStore.interval = days
    }

    fun changePace(percentPerDay: Double) {
        if (!percentPerDay.isFinite() || percentPerDay !in 0.1..20.0) return
        pace = percentPerDay
        settingsStore.pace = percentPerDay
    }

    fun checkUpdates() {
        updateStatus = "Проверка недоступна: сервер обновлений не подключён."
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