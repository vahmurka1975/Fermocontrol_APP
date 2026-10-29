package ru.fermocontrol.app

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupPanel(
    products: List<Product>,
    store: ProductStore,
    available: Boolean,
    journalAvailable: Boolean,
    onBusy: (Boolean) -> Unit,
    onRestored: (List<Product>) -> Unit
) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val scope = rememberCoroutineScope()

    var menu by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<Product>?>(null) }
    var recovery by remember { mutableStateOf(false) }
    var checkingRecovery by remember { mutableStateOf(true) }

    var yandexToken by rememberSaveable { mutableStateOf(CloudBackupManager.getYandexToken(context)) }
    var lastSyncTime by rememberSaveable { mutableStateOf(CloudBackupManager.getLastSyncTime(context)) }
    var tokenDialog by rememberSaveable { mutableStateOf(false) }
    var tokenDraft by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }

    var autoBackups by remember { mutableStateOf<List<AutoBackupItem>>(emptyList()) }
    var autoBackupChoices by remember { mutableStateOf<List<AutoBackupItem>?>(null) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    val latestProducts by rememberUpdatedState(products)
    val latestAvailable by rememberUpdatedState(available)
    val latestJournal by rememberUpdatedState(journalAvailable)

    val currentVersion = remember(context) {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.5"
        } catch (_: Exception) {
            "1.0.5"
        }
    }

    LaunchedEffect(store) {
        try {
            recovery = withContext(Dispatchers.IO) { store.hasRecoveryCopy() }
        } finally {
            checkingRecovery = false
        }
    }

    LaunchedEffect(products, journalAvailable) {
        if (journalAvailable && products.isNotEmpty()) {
            delay(500)
            withContext(Dispatchers.IO) {
                runCatching {
                    CloudBackupManager.saveAutoBackup(context, BackupCodec.encode(products))
                }
            }
        }
        autoBackups = CloudBackupManager.listAutoBackups(context)
    }

    fun work(task: suspend () -> Unit) {
        if (!latestAvailable) {
            message = "Дождитесь окончания текущей операции и повторите попытку."
            return
        }
        onBusy(true)
        scope.launch {
            try {
                task()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "Операция не завершена: " + (error.localizedMessage ?: "ошибка")
            } finally {
                onBusy(false)
            }
        }
    }

    fun write(uri: Uri, bytes: ByteArray) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Не удалось открыть файл для записи")
        output.use {
            it.write(bytes)
            it.flush()
        }
    }

    fun filename(extension: String): String =
        "Fermocontrol_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) +
            ".$extension"

    val saveJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) work {
            check(latestJournal) { "Журнал не прочитан. Сначала восстановите данные." }
            val snapshot = latestProducts
            withContext(Dispatchers.IO) { write(uri, BackupCodec.encode(snapshot)) }
            message = "Резервная копия сохранена: ${snapshot.size} продуктов, включая архив."
        }
    }

    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) work {
            check(latestJournal) { "Журнал не прочитан. Сначала восстановите данные." }
            val snapshot = latestProducts
            withContext(Dispatchers.IO) { write(uri, CsvExporter.encode(snapshot)) }
            message = "CSV сохранён. В файле есть начальный вес, взвешивания и наблюдения. Даты указаны в UTC."
        }
    }

    val saveRecovery = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) work {
            withContext(Dispatchers.IO) { write(uri, store.recoveryBytes()) }
            message = "Локальная копия перед последним восстановлением сохранена в выбранный файл."
        }
    }

    val openJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) work {
            preview = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть выбранный файл")
                input.use { BackupCodec.decode(BackupCodec.read(it)) }
            }
            menu = false
        }
    }

    TextButton(enabled = available, onClick = { menu = true }) {
        Text("Данные")
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { if (available) menu = false },
            title = { Text("Резервные копии и обновления") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Файловые и облачные копии",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("JSON сохраняет все продукты и журналы. CSV предназначен для просмотра в Excel и не подходит для восстановления.")

                    Button(
                        enabled = available && journalAvailable,
                        onClick = {
                            work {
                                check(latestJournal) { "Журнал не прочитан. Сначала восстановите данные." }
                                val snapshot = latestProducts
                                val name = filename("json")
                                val bytes = withContext(Dispatchers.IO) { BackupCodec.encode(snapshot) }
                                menu = false
                                CloudBackupManager.shareBackupFile(activityContext, name, bytes)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Отправить копию в облако (Яндекс / Google Диск)") }

                    OutlinedButton(
                        enabled = available && journalAvailable,
                        onClick = {
                            try {
                                saveJson.launch(filename("json"))
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "Недоступен выбор файла"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Сохранить JSON-копию в телефон") }

                    OutlinedButton(
                        enabled = available && journalAvailable,
                        onClick = {
                            try {
                                saveCsv.launch(filename("csv"))
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "Недоступен выбор файла"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Экспорт журнала CSV") }

                    OutlinedButton(
                        enabled = available,
                        onClick = {
                            try {
                                openJson.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "Недоступен выбор файла"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Восстановить из JSON-файла") }

                    HorizontalDivider()

                    Text(
                        "Яндекс Диск (в 1 клик по OAuth-токену)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (yandexToken.isBlank()) {
                            "Токен не указан. Укажите OAuth-токен для прямой выгрузки и восстановления одной кнопкой."
                        } else {
                            "Токен сохранён." + (lastSyncTime?.let { "\nПоследняя синхронизация: $it" } ?: "")
                        }
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            enabled = available && journalAvailable,
                            onClick = {
                                if (yandexToken.isBlank()) {
                                    tokenDraft = ""
                                    tokenDialog = true
                                } else {
                                    work {
                                        check(latestJournal) { "Журнал не прочитан. Сначала восстановите данные." }
                                        val snapshot = latestProducts
                                        val bytes = withContext(Dispatchers.IO) { BackupCodec.encode(snapshot) }
                                        val syncAt = CloudBackupManager.uploadToYandexDisk(context, bytes)
                                        lastSyncTime = syncAt
                                        menu = false
                                        message = "Копия выгружена на Яндекс Диск (${snapshot.size} продуктов).\nСинхронизация: $syncAt"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Выгрузить") }

                        OutlinedButton(
                            enabled = available,
                            onClick = {
                                if (yandexToken.isBlank()) {
                                    tokenDraft = ""
                                    tokenDialog = true
                                } else {
                                    work {
                                        val bytes = CloudBackupManager.downloadFromYandexDisk(context)
                                        val decoded = withContext(Dispatchers.IO) { BackupCodec.decode(bytes) }
                                        lastSyncTime = CloudBackupManager.getLastSyncTime(context)
                                        preview = decoded
                                        menu = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Восстановить") }
                    }

                    TextButton(
                        enabled = available,
                        onClick = {
                            tokenDraft = yandexToken
                            showToken = false
                            tokenDialog = true
                        }
                    ) {
                        Text(if (yandexToken.isBlank()) "Указать OAuth-токен Яндекс Диска" else "Изменить OAuth-токен")
                    }

                    HorizontalDivider()

                    Text(
                        "Локальная автозащита",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (autoBackups.isEmpty()) {
                            "Автокопия создаётся автоматически при работе с журналом."
                        } else {
                            "Последняя автокопия: ${autoBackups.first().updatedAt} (доступно снапшотов: ${autoBackups.size})."
                        }
                    )

                    OutlinedButton(
                        enabled = available && autoBackups.isNotEmpty(),
                        onClick = {
                            work {
                                val items = CloudBackupManager.listAutoBackups(context)
                                autoBackups = items
                                when {
                                    items.isEmpty() -> message = "Локальные автокопии пока отсутствуют."
                                    items.size == 1 -> {
                                        val bytes = CloudBackupManager.readAutoBackup(items.first())
                                        preview = withContext(Dispatchers.IO) { BackupCodec.decode(bytes) }
                                        menu = false
                                    }
                                    else -> {
                                        autoBackupChoices = items
                                        menu = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Восстановить из автокопии") }

                    Text("Перед любой заменой журнал также сохраняется во внутреннюю страховочную копию.")

                    OutlinedButton(
                        enabled = available && recovery && !checkingRecovery,
                        onClick = {
                            work {
                                preview = withContext(Dispatchers.IO) {
                                    BackupCodec.decode(store.recoveryBytes())
                                }
                                menu = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Вернуть копию до последнего восстановления") }

                    TextButton(
                        enabled = available && recovery && !checkingRecovery,
                        onClick = {
                            try {
                                saveRecovery.launch("Fermocontrol_before_restore.json")
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "Недоступен выбор файла"
                            }
                        }
                    ) { Text("Сохранить эту копию в файл") }

                    HorizontalDivider()

                    Text("Текущая версия: $currentVersion")

                    OutlinedButton(
                        enabled = available && !downloading,
                        onClick = {
                            work {
                                val info: UpdateInfo = UpdateManager.checkUpdate(currentVersion)
                                menu = false
                                if (info.hasUpdate) {
                                    updateInfo = info
                                } else {
                                    message = "У вас установлена актуальная версия ($currentVersion).\n${info.releaseNotes}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Проверить обновления") }

                    Text("Файлы не зашифрованы. Храните облачную или внешнюю JSON-копию, чтобы защитить журнал при потере телефона.")
                }
            },
            confirmButton = {
                TextButton(enabled = available, onClick = { menu = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (tokenDialog) {
        AlertDialog(
            onDismissRequest = { tokenDialog = false },
            title = { Text("OAuth-токен Яндекс Диска") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Вставьте OAuth-токен Яндекс Диска с правами доступа к Диску. " +
                            "Копия будет сохраняться в папку «Fermocontrol/fermocontrol_backup.json»."
                    )
                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        label = { Text("OAuth-токен") },
                        singleLine = true,
                        visualTransformation = if (showToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "Скрыть" else "Показать")
                        }
                        if (yandexToken.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    CloudBackupManager.clearYandexToken(context)
                                    yandexToken = ""
                                    tokenDraft = ""
                                    tokenDialog = false
                                    message = "Токен Яндекс Диска удалён с устройства."
                                }
                            ) { Text("Удалить токен") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        CloudBackupManager.saveYandexToken(context, tokenDraft)
                        yandexToken = CloudBackupManager.getYandexToken(context)
                        tokenDialog = false
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { tokenDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    autoBackupChoices?.let { choices ->
        AlertDialog(
            onDismissRequest = { if (available) autoBackupChoices = null },
            title = { Text("Выберите автокопию") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Доступны два последних различающихся локальных состояния журнала:")
                    choices.forEach { item ->
                        OutlinedButton(
                            enabled = available,
                            onClick = {
                                work {
                                    val bytes = CloudBackupManager.readAutoBackup(item)
                                    preview = withContext(Dispatchers.IO) { BackupCodec.decode(bytes) }
                                    autoBackupChoices = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${item.title}\n(${item.updatedAt})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = available,
                    onClick = { autoBackupChoices = null }
                ) { Text("Отмена") }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!downloading) updateInfo = null },
            title = { Text("Доступно обновление ${info.latestVersion}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Текущая версия: $currentVersion\nНовая версия: ${info.latestVersion}")
                    if (info.releaseNotes.isNotBlank()) {
                        Text(info.releaseNotes)
                    }
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Загрузка: $downloadProgress%")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        downloadProgress = 0
                        scope.launch {
                            val result = UpdateManager.downloadAndInstallApk(
                                context = activityContext,
                                downloadUrl = info.downloadUrl,
                                onProgress = { progress -> downloadProgress = progress }
                            )
                            downloading = false
                            updateInfo = null
                            result.onFailure { err ->
                                message = "Ошибка обновления: " + (err.localizedMessage ?: "не удалось скачать APK")
                            }
                        }
                    }
                ) { Text("Скачать и установить") }
            },
            dismissButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = { updateInfo = null }
                ) { Text("Позже") }
            }
        )
    }

    preview?.let { incoming ->
        val active = incoming.count { it.archivedAt == null }
        val records = incoming.sumOf { it.weighings.size }
        AlertDialog(
            onDismissRequest = { if (available) preview = null },
            title = { Text("Заменить текущие данные?") },
            text = {
                Text(
                    "В файле: активных продуктов — $active, в архиве — ${incoming.size - active}, " +
                        "взвешиваний — $records.\n\n" +
                        "Текущих продуктов: ${products.size}. Они будут ЗАМЕНЕНЫ, а не объединены с файлом. " +
                        "Перед заменой сохранится локальная копия текущего журнала. " +
                        "Номера, даты и история из файла сохранятся без изменений."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = available,
                    onClick = {
                        work {
                            withContext(Dispatchers.IO) { store.restore(incoming) }
                            onRestored(incoming)
                            recovery = true
                            preview = null
                            menu = false
                            message = "Данные восстановлены. Предыдущий журнал доступен через «Данные → Вернуть копию до последнего восстановления»."
                        }
                    }
                ) { Text("Заменить данные") }
            },
            dismissButton = {
                TextButton(enabled = available, onClick = { preview = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Работа с данными") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}