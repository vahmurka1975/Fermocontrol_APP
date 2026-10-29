package ru.fermocontrol.app

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.Manifest
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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
    var autoSync by rememberSaveable { mutableStateOf(CloudBackupManager.getAutoSyncEnabled(context)) }
    var lastSyncTime by rememberSaveable { mutableStateOf(CloudBackupManager.getLastSyncTime(context)) }
    var tokenDialog by rememberSaveable { mutableStateOf(false) }
    var webAuthDialog by rememberSaveable { mutableStateOf(false) }
    var tokenDraft by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var verifyingToken by remember { mutableStateOf(false) }

    var autoBackups by remember { mutableStateOf<List<AutoBackupItem>>(emptyList()) }
    var autoBackupChoices by remember { mutableStateOf<List<AutoBackupItem>?>(null) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    var remindersEnabled by remember { mutableStateOf(ReminderScheduler.areRemindersEnabled(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.setRemindersEnabled(context, true)
            remindersEnabled = true
            ReminderScheduler.checkAndNotifyIfNeeded(context, force = true)
            message = "Уведомления о взвешивании включены."
        } else {
            message = "Разрешение на показ уведомлений не предоставлено в настройках Android."
        }
    }

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
            pInfo.versionName ?: "1.0.10"
        } catch (_: Exception) {
            "1.0.10"
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
            delay(800)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { BackupCodec.encode(products) }.getOrNull()
            }
            if (bytes != null) {
                val changed = runCatching {
                    CloudBackupManager.saveAutoBackup(context, bytes)
                }.getOrDefault(false)

                if (changed &&
                    CloudBackupManager.getAutoSyncEnabled(context) &&
                    CloudBackupManager.getYandexToken(context).isNotBlank()
                ) {
                    runCatching {
                        CloudBackupManager.uploadToYandexDisk(context, bytes)
                    }.onSuccess { syncAt ->
                        lastSyncTime = syncAt
                    }
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
                        "Быстрая отправка в облако",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("Отправьте JSON-файл в установленное приложение Яндекс Диска, Google Диска или в Telegram.")

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
                        "Яндекс Диск (прямая синхронизация)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (yandexToken.isBlank()) {
                            "Токен не указан. Настройте OAuth-токен для выгрузки и восстановления в 1 клик."
                        } else {
                            "Токен: ${CloudBackupManager.maskToken(yandexToken)}" +
                                (lastSyncTime?.let { "\nПоследняя синхронизация: $it" } ?: "")
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

                    if (yandexToken.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Автовыгрузка при изменениях",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoSync,
                                enabled = available,
                                onCheckedChange = { checked ->
                                    autoSync = checked
                                    CloudBackupManager.setAutoSyncEnabled(context, checked)
                                    if (checked && latestJournal && latestProducts.isNotEmpty()) {
                                        scope.launch {
                                            runCatching {
                                                val bytes = withContext(Dispatchers.IO) {
                                                    BackupCodec.encode(latestProducts)
                                                }
                                                CloudBackupManager.uploadToYandexDisk(context, bytes)
                                            }.onSuccess { syncAt ->
                                                lastSyncTime = syncAt
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    TextButton(
                        enabled = available,
                        onClick = {
                            yandexToken = CloudBackupManager.getYandexToken(context)
                            tokenDraft = yandexToken
                            showToken = false
                            tokenDialog = true
                        }
                    ) {
                        Text(if (yandexToken.isBlank()) "Подключить Яндекс Диск (OAuth)" else "Настроить OAuth-токен")
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
                            "Последняя автокопия: ${autoBackups.first().updatedAt} (снапшотов: ${autoBackups.size})."
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
            onDismissRequest = { if (!verifyingToken) tokenDialog = false },
            title = { Text("OAuth-токен Яндекс Диска") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Нажмите «Войти через Яндекс» и на открывшейся странице Полигона нажмите " +
                            "кнопку «Получить OAuth-токен» — токен (начинается с y0_) проверится и сохранится автоматически."
                    )

                    Button(
                        enabled = !verifyingToken,
                        onClick = { webAuthDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Войти через Яндекс (автополучение)")
                    }

                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        label = { Text("OAuth-токен (y0_...)") },
                        singleLine = true,
                        visualTransformation = if (showToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !verifyingToken,
                            onClick = { showToken = !showToken }
                        ) {
                            Text(if (showToken) "Скрыть" else "Показать")
                        }
                        TextButton(
                            enabled = !verifyingToken,
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = cm?.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    .orEmpty()
                                val cleaned = CloudBackupManager.normalizeToken(clipText)
                                if (cleaned.isNotBlank()) {
                                    tokenDraft = cleaned
                                    showToken = true
                                } else {
                                    message = "Буфер обмена пуст."
                                }
                            }
                        ) {
                            Text("Вставить из буфера")
                        }
                    }

                    if (verifyingToken) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Проверяем токен на сервере Яндекс Диска…")
                    }

                    if (yandexToken.isNotBlank() && !verifyingToken) {
                        TextButton(
                            onClick = {
                                CloudBackupManager.clearYandexToken(context)
                                yandexToken = ""
                                autoSync = false
                                tokenDraft = ""
                                tokenDialog = false
                                message = "Токен Яндекс Диска удалён с устройства."
                            }
                        ) { Text("Удалить токен") }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !verifyingToken,
                    onClick = {
                        val cleaned = CloudBackupManager.normalizeToken(tokenDraft)
                        if (cleaned.isBlank()) {
                            CloudBackupManager.clearYandexToken(context)
                            yandexToken = ""
                            autoSync = false
                            tokenDialog = false
                        } else {
                            verifyingToken = true
                            scope.launch {
                                val checkResult = CloudBackupManager.verifyYandexToken(cleaned)
                                verifyingToken = false
                                checkResult.onSuccess { account ->
                                    CloudBackupManager.saveYandexToken(context, cleaned)
                                    yandexToken = CloudBackupManager.getYandexToken(context)
                                    tokenDraft = yandexToken
                                    tokenDialog = false
                                    message = "Токен Яндекс Диска проверен и сохранён.\nАккаунт: $account"
                                }.onFailure { err ->
                                    if (err is IOException) {
                                        CloudBackupManager.saveYandexToken(context, cleaned)
                                        yandexToken = CloudBackupManager.getYandexToken(context)
                                        tokenDialog = false
                                        message = "Токен сохранён без онлайн-проверки (нет соединения с сетью)."
                                    } else {
                                        tokenDraft = cleaned
                                        showToken = true
                                        message = err.localizedMessage ?: "Токен отклонён Яндекс Диском."
                                    }
                                }
                            }
                        }
                    }
                ) { Text("Сохранить и проверить") }
            },
            dismissButton = {
                TextButton(
                    enabled = !verifyingToken,
                    onClick = { tokenDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (webAuthDialog) {
        YandexTokenWebDialog(
            onDismiss = { webAuthDialog = false },
            onTokenVerified = { verifiedToken, account ->
                CloudBackupManager.saveYandexToken(context, verifiedToken)
                yandexToken = CloudBackupManager.getYandexToken(context)
                tokenDraft = yandexToken
                webAuthDialog = false
                tokenDialog = false
                message = "Яндекс Диск успешно подключён!\nАккаунт: $account"
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YandexTokenWebDialog(
    onDismiss: () -> Unit,
    onTokenVerified: (token: String, account: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var statusText by remember {
        mutableStateOf("Нажмите на странице кнопку «Получить OAuth-токен» и войдите в Яндекс")
    }

    val scanAndFixLinksJs = """
        (function() {
            try {
                window.open = function(u) {
                    if (u) window.location.href = u;
                    return window;
                };
                var links = document.querySelectorAll('a[target="_blank"]');
                for (var i = 0; i < links.length; i++) {
                    links[i].removeAttribute('target');
                }
                var h = window.location.hash || '';
                var m = h.match(/[#&?]access_token=([^&#\s"']+)/);
                if (m && m[1]) return decodeURIComponent(m[1]);
                var inputs = document.querySelectorAll('input');
                for (var j = 0; j < inputs.length; j++) {
                    var v = (inputs[j].value || '').trim();
                    if (/^(y0_[A-Za-z0-9_-]{25,}|AQAAA[A-Za-z0-9_-]{20,})$/.test(v)) {
                        return v;
                    }
                }
            } catch (e) {}
            return '';
        })();
    """.trimIndent()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }

                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        var delivered = false
                        val rejectedTokens = mutableSetOf<String>()
                        val testingTokens = mutableSetOf<String>()

                        fun tryCandidate(rawCandidate: String?) {
                            if (delivered) return
                            val candidate = CloudBackupManager.normalizeToken(rawCandidate)
                            if (!CloudBackupManager.looksLikeYandexToken(candidate)) return
                            if (candidate in rejectedTokens || candidate in testingTokens) return

                            testingTokens += candidate
                            statusText = "Проверяем полученный токен…"
                            scope.launch {
                                val check = CloudBackupManager.verifyYandexToken(candidate)
                                testingTokens -= candidate
                                check.onSuccess { account ->
                                    if (!delivered) {
                                        delivered = true
                                        onTokenVerified(candidate, account)
                                    }
                                }.onFailure {
                                    rejectedTokens += candidate
                                    statusText = "Старый токен истёк — нажмите «Получить OAuth-токен» на странице"
                                }
                            }
                        }

                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(false)
                            settings.userAgentString = settings.userAgentString
                                .replace("; wv", "")
                                .replace("Version/4.0 ", "")

                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = request?.url?.toString()
                                    CloudBackupManager.extractTokenFromUrl(targetUrl)?.let {
                                        tryCandidate(it)
                                    }
                                    return false
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    loading = true
                                    CloudBackupManager.extractTokenFromUrl(url)?.let {
                                        tryCandidate(it)
                                    }
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    CloudBackupManager.extractTokenFromUrl(url)?.let {
                                        tryCandidate(it)
                                    }
                                    view?.evaluateJavascript(scanAndFixLinksJs) { raw ->
                                        tryCandidate(raw)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    CloudBackupManager.extractTokenFromUrl(url)?.let {
                                        tryCandidate(it)
                                    }
                                    view?.evaluateJavascript(scanAndFixLinksJs) { raw ->
                                        tryCandidate(raw)
                                    }
                                    view?.postDelayed({
                                        if (!delivered) {
                                            view.evaluateJavascript(scanAndFixLinksJs) { raw ->
                                                tryCandidate(raw)
                                            }
                                        }
                                    }, 1200)
                                }
                            }

                            loadUrl(CloudBackupManager.YANDEX_POLIGON_URL)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    }
                )
            }
        }
    }
}