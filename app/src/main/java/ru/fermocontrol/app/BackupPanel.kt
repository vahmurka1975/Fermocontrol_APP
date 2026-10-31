package ru.fermocontrol.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

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
            pInfo.versionName ?: "1.4.0"
        } catch (_: Exception) {
            "1.4.0"
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

    // Вход в панель через иконку шестерёнки
    FilledTonalIconButton(
        enabled = available,
        onClick = { menu = true },
        modifier = Modifier.semantics { contentDescription = "Настройки и резервные копии" }
    ) {
        SettingsGearIcon(
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }

    if (menu) {
        Dialog(
            onDismissRequest = { if (available) menu = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 760.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    // Заголовок окна
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Настройки и данные",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            enabled = available,
                            onClick = { menu = false }
                        ) {
                            Text("Закрыть")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // =====================================================================
                        // РАЗДЕЛ 1. РЕЗЕРВНОЕ КОПИРОВАНИЕ В ОБЛАКО
                        // =====================================================================
                        SectionCard(title = "Резервное копирование в облако") {
                            val isTokenConnected = yandexToken.isNotBlank()

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTokenConnected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Яндекс Диск",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isTokenConnected) {
                                            "Статус токена: подключён ✅"
                                        } else {
                                            "Статус токена: не подключён"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isTokenConnected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (isTokenConnected && !lastSyncTime.isNullOrBlank()) {
                                        Text(
                                            text = "Последняя синхронизация: $lastSyncTime",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

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
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Выгрузить",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

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
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Восстановить",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isTokenConnected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Автовыгрузка при изменениях",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
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

                            OutlinedButton(
                                enabled = available,
                                onClick = {
                                    yandexToken = CloudBackupManager.getYandexToken(context)
                                    tokenDraft = yandexToken
                                    showToken = false
                                    tokenDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (yandexToken.isBlank()) {
                                        "Подключить Яндекс Диск"
                                    } else {
                                        "Настроить подключение к Яндекс Диску"
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            Text(
                                text = "Отправка файла через другое приложение (Яндекс Диск, Google Диск, Telegram):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
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
                            ) {
                                Text("Поделиться копией в облако")
                            }
                        }

                        // =====================================================================
                        // РАЗДЕЛ 2. ЛОКАЛЬНОЕ РЕЗЕРВНОЕ КОПИРОВАНИЕ
                        // =====================================================================
                        SectionCard(title = "Локальное резервное копирование") {
                            Text(
                                text = "Файлы на устройстве",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    enabled = available && journalAvailable,
                                    onClick = {
                                        try {
                                            saveJson.launch(filename("json"))
                                        } catch (e: Exception) {
                                            message = e.localizedMessage ?: "Недоступен выбор файла"
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Сохранить JSON",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    enabled = available,
                                    onClick = {
                                        try {
                                            openJson.launch(
                                                arrayOf(
                                                    "application/json",
                                                    "text/*",
                                                    "application/octet-stream"
                                                )
                                            )
                                        } catch (e: Exception) {
                                            message = e.localizedMessage ?: "Недоступен выбор файла"
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Загрузить JSON",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

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
                            ) {
                                Text("Экспорт таблицы в CSV (Excel)")
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            Text(
                                text = "Автоматическая страховка",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (autoBackups.isEmpty()) {
                                    "Автокопия создаётся автоматически при работе с журналом."
                                } else {
                                    "Последняя автокопия: ${autoBackups.first().updatedAt} (доступно: ${autoBackups.size})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            ) {
                                Text("Восстановить из автокопии")
                            }

                            if (recovery && !checkingRecovery) {
                                OutlinedButton(
                                    enabled = available,
                                    onClick = {
                                        work {
                                            preview = withContext(Dispatchers.IO) {
                                                BackupCodec.decode(store.recoveryBytes())
                                            }
                                            menu = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Откатить последнее восстановление")
                                }

                                TextButton(
                                    enabled = available,
                                    onClick = {
                                        try {
                                            saveRecovery.launch("Fermocontrol_before_restore.json")
                                        } catch (e: Exception) {
                                            message = e.localizedMessage ?: "Недоступен выбор файла"
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Сохранить страховочную копию в файл")
                                }
                            }
                        }

                        // =====================================================================
                        // РАЗДЕЛ 3. ОБНОВЛЕНИЕ И УВЕДОМЛЕНИЯ
                        // =====================================================================
                        SectionCard(title = "Обновление и уведомления") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Версия приложения",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentVersion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

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
                            ) {
                                Text("Проверить обновления")
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "Напоминания о взвешивании",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Уведомлять, если продукт не взвешивался более 3 дней",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = remindersEnabled,
                                    enabled = available,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                ReminderScheduler.setRemindersEnabled(context, true)
                                                remindersEnabled = true
                                                ReminderScheduler.checkAndNotifyIfNeeded(context, force = true)
                                            }
                                        } else {
                                            ReminderScheduler.setRemindersEnabled(context, false)
                                            remindersEnabled = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (tokenDialog) {
        AlertDialog(
            onDismissRequest = { if (!verifyingToken) tokenDialog = false },
            title = { Text("Подключение Яндекс Диска") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Нажмите «Войти через Яндекс» и на открывшейся странице Полигона нажмите " +
                            "кнопку «Получить OAuth-токен» — токен проверится и сохранится автоматически."
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
                        label = { Text("Или вставьте OAuth-токен вручную") },
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
                                message = "Яндекс Диск отключён, токен удалён с устройства."
                            }
                        ) { Text("Отключить Яндекс Диск") }
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
                                    message = "Яндекс Диск подключён.\nАккаунт: $account"
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
                            message = "Данные восстановлены. Предыдущий журнал доступен через «Откатить последнее восстановление»."
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
            title = { Text("Настройки и данные") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingsGearIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rOuter = size.minDimension * 0.44f
        val rBody = size.minDimension * 0.30f
        val rInner = size.minDimension * 0.13f
        val strokeW = size.minDimension * 0.10f

        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            drawLine(
                color = tint,
                start = Offset(cx + rBody * 0.85f * cosA, cy + rBody * 0.85f * sinA),
                end = Offset(cx + rOuter * cosA, cy + rOuter * sinA),
                strokeWidth = strokeW * 1.15f,
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = tint,
            radius = rBody,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW)
        )

        drawCircle(
            color = tint,
            radius = rInner,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW * 0.85f)
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