package ru.fermocontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import ru.fermocontrol.ui.theme.AppPalette
import ru.fermocontrol.ui.theme.AppPalettes
import ru.fermocontrol.ui.theme.LocalAppPalette
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.fermocontrol.data.ExternalBackup

// ---- Палитра из CSS .backup-* ----
private val Amber = Color(0xFFEDC58C) // предупреждение, не зависит от темы

private val TextMain: Color @Composable get() = LocalAppPalette.current.ink
private val TextSoft: Color @Composable get() = LocalAppPalette.current.soft
private val TextStrong: Color @Composable get() = LocalAppPalette.current.strong
private val TextBody: Color @Composable get() = LocalAppPalette.current.body
private val TextMuted: Color @Composable get() = LocalAppPalette.current.muted
private val Kicker: Color @Composable get() = LocalAppPalette.current.kicker
private val CardBorder: Color @Composable get() = LocalAppPalette.current.cardLine
private val Line: Color @Composable get() = LocalAppPalette.current.divider
private val CardTop: Color @Composable get() = LocalAppPalette.current.card
private val CardBottom: Color @Composable get() = LocalAppPalette.current.cardEnd
private val ChipBg: Color @Composable get() = LocalAppPalette.current.chip
private val ChipLine: Color @Composable get() = LocalAppPalette.current.chipLine
private val ChipIcon: Color @Composable get() = LocalAppPalette.current.chipIcon
private val FieldBg: Color @Composable get() = LocalAppPalette.current.field
private val FieldLine: Color @Composable get() = LocalAppPalette.current.fieldLine
private val SelectedBg: Color @Composable get() = LocalAppPalette.current.selected
private val SelectedLine: Color @Composable get() = LocalAppPalette.current.selectedLine
private val Label: Color @Composable get() = LocalAppPalette.current.label
private val ActionText: Color @Composable get() = LocalAppPalette.current.action
private val Accent: Color @Composable get() = LocalAppPalette.current.accent
private val AccentLine: Color @Composable get() = LocalAppPalette.current.accentLine
private val OnAccent: Color @Composable get() = LocalAppPalette.current.onAccent
private val BtnSec: Color @Composable get() = LocalAppPalette.current.secondary
private val BtnSecLine: Color @Composable get() = LocalAppPalette.current.secondaryLine
private val OnSecondary: Color @Composable get() = LocalAppPalette.current.onSecondary

enum class CloudProvider { Yandex, Google }

data class AutoBackupItem(val id: String, val title: String, val subtitle: String)

data class SettingsUiState(
    val cloud: CloudProvider = CloudProvider.Yandex,
    val activeCount: Int = 0,
    val archiveCount: Int = 0,
    val logCount: Int = 0,
    val storageStatus: String = "Проверка локального хранилища.",
    val backupStatus: String = "Передача JSON на скачивание ещё не запускалась.",
    val autoBackups: List<AutoBackupItem> = emptyList(),
    val autoBackupStatus: String = "",
    val canRollback: Boolean = false,
    val canSaveRecovery: Boolean = false,
    val recoveryStatus: String = "",
    val updateStatus: String = "Сервер обновлений и установка Android-сборок в макете не подключены.",
    val versionTag: String = "v0.4.1",
    val interval: Int = 3,
    val pace: Double = 1.5,
	val paletteId: String = "forest",
)

class SettingsActions(
    val onClose: () -> Unit = {},
    val onCloudChange: (CloudProvider) -> Unit = {},
    val onConnectCloud: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onCsv: () -> Unit = {},
    val onRestoreAuto: (String) -> Unit = {},
    val onRollback: () -> Unit = {},
    val onSaveRecovery: () -> Unit = {},
    val onClearAutoBackups: () -> Unit = {},
    val onCheckUpdates: () -> Unit = {},
    val onIntervalChange: (Int) -> Unit = {},
    val onPaceChange: (Double) -> Unit = {},
    val onGuide: () -> Unit = {},
    val onResetDemo: () -> Unit = {},
	val onPaletteChange: (String) -> Unit = {},
)

/** Содержимое листа «Настройки». Оболочку (фон, скругление, scrim) даёт ваш ModalBottomSheet. */
@Composable
fun SettingsSheet(state: SettingsUiState, actions: SettingsActions, externalBackup: ExternalBackup) {
    val width = LocalConfiguration.current.screenWidthDp
    val narrow = width <= 460
    val tiny = width <= 355
    val hPad = if (narrow) 16.dp else 20.dp

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = hPad, end = hPad, bottom = 25.dp),
    ) {
        // .sheettop
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KickerText("ФермоКонтроль")
                Text("Настройки", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight(550))
                Text("Резервные копии и обновление", color = TextBody, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Box(
                Modifier.size(44.dp).clickable(role = Role.Button, onClick = actions.onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Закрыть настройки", tint = TextSoft) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
    // Облако отключено: резервное копирование на Яндекс Диск и Google Диск пока не делаем.
    // CloudCard(state, actions, narrow, tiny)
    LocalCard(state, actions, narrow, tiny)
    ExternalBackupCard(externalBackup, narrow, tiny)
    ThemeCard(state, actions, narrow)
    UpdatesCard(state, actions)
}

        ControlParams(state, actions)

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextAction("Как читать показатели", null, actions.onGuide)
            TextAction("Восстановить демо-данные", null, actions.onResetDemo, color = Color(0xFFC58A82))
        }
        Text(
            "Локальные копии работают без облака. Формат файла предназначен для этого макета.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 18.dp),
        )
    }
}

// ---------------- Карточки ----------------
/* Облако временно отключено. Чтобы вернуть, уберите эти маркеры и раскомментируйте вызов в SettingsSheet.
@Composable
private fun CloudCard(s: SettingsUiState, a: SettingsActions, narrow: Boolean, tiny: Boolean) {
    val name = if (s.cloud == CloudProvider.Yandex) "Яндекс Диск" else "Google Диск"
    BackupCard(Icons.Outlined.Cloud, "Облако", "Резервное копирование в облако", narrow) {
        // .cloud-options: две колонки, на узких одна
        val options = listOf(CloudProvider.Yandex to "Яндекс Диск", CloudProvider.Google to "Google Диск")
        if (width390()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (p, t) -> CloudChoice(t, s.cloud == p) { a.onCloudChange(p) } }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (p, t) ->
                    CloudChoice(t, s.cloud == p, Modifier.weight(1f)) { a.onCloudChange(p) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StatusRow(name) { ConnectionPill("Не подключён") }
        StatusRow("Последняя подтверждённая копия") {
            Text("Нет данных от сервиса", color = TextStrong, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
        }
        BackupButton("О подключении $name", Icons.Outlined.Link, a.onConnectCloud, Modifier.fillMaxWidth())
        ButtonPair(tiny) {
            BackupButton("Выгрузить", Icons.Outlined.FileUpload, {}, it, enabled = false, compact = narrow)
            BackupButton("Восстановить", Icons.Outlined.FileDownload, {}, it, enabled = false, compact = narrow)
        }
        Muted(
            "Оба сервиса пока не подключены: вход, выгрузка и подтверждение сохранения не выполняются. " +
                "Это резервное копирование, не синхронизация журналов.",
        )
    }
}
*/

@Composable
private fun LocalCard(s: SettingsUiState, a: SettingsActions, narrow: Boolean, tiny: Boolean) {
    BackupCard(Icons.Outlined.PhoneAndroid, "На устройстве", "Локальное резервное копирование", narrow) {
        Row(Modifier.padding(bottom = 15.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Stat(s.activeCount, "В работе", Modifier.weight(1f))
            Stat(s.archiveCount, "В архиве", Modifier.weight(1f))
            Stat(s.logCount, "Взвешиваний", Modifier.weight(1f))
        }
        ButtonPair(tiny) {
            BackupButton("Выгрузить", Icons.Outlined.FileDownload, a.onExport, it, main = true, compact = narrow)
            BackupButton("Восстановить", Icons.Outlined.FileUpload, a.onImport, it, compact = narrow)
        }
        Body(s.storageStatus)
        Body(s.backupStatus)
        Muted("Браузер не сообщает, сохранён ли файл на устройстве. Проверьте папку загрузок самостоятельно.")
        Muted("JSON сохраняет продукты, архив, журнал и добавленные фотографии. Восстановление — только после проверки и подтверждения.")

        TextAction("Поделиться копией", Icons.Outlined.Share, a.onShare, Modifier.padding(top = 10.dp))

        // details.backup-history
        var open by remember { mutableStateOf(false) }
        Column(Modifier.padding(top = 12.dp).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 32.dp)
                    .clickable(role = Role.Button) { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, null, tint = ActionText,
                    modifier = Modifier.size(15.dp).rotate(if (open) 90f else 0f))
                Text("Автокопии и откат", color = ActionText, fontSize = 11.sp)
            }
            if (open) {
                Column(Modifier.padding(top = 10.dp)) {
                    if (s.autoBackups.isEmpty()) {
                        Text("Автокопий пока нет.", color = TextMuted, fontSize = 11.sp,
                            lineHeight = 17.6.sp, modifier = Modifier.padding(top = 2.dp, bottom = 5.dp))
                    }
                    s.autoBackups.forEachIndexed { i, item ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(item.subtitle, color = TextMuted, fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 3.dp))
                            }
                            val sh = RoundedCornerShape(11.dp)
                            Box(
                                Modifier.size(38.dp).clip(sh).background(BtnSec)
                                    .border(1.dp, FieldLine, sh)
                                    .clickable(role = Role.Button) { a.onRestoreAuto(item.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, "Восстановить из автокопии",
                                    tint = ActionText, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    if (s.autoBackupStatus.isNotEmpty()) Muted(s.autoBackupStatus)
                    if (s.canRollback) {
                        BackupButton("Откатить восстановление", Icons.AutoMirrored.Outlined.Undo, a.onRollback,
                            Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                    if (s.canSaveRecovery) {
                        TextAction("Сохранить страховочную копию", null, a.onSaveRecovery,
                            Modifier.padding(top = 9.dp))
                    }
                    if (s.recoveryStatus.isNotEmpty()) Muted(s.recoveryStatus)
                    TextAction("Удалить только автокопии", null, a.onClearAutoBackups, minHeight = 40.dp)
                    Muted("Автокопии хранятся только в этом браузере и пропадут при очистке его данных. " +
                        "Журнал и страховочная копия при удалении автокопий не затрагиваются.")
                }
            }
        }
    }
}

@Composable
private fun UpdatesCard(s: SettingsUiState, a: SettingsActions) {
    BackupCard(
        Icons.Outlined.Refresh, "Приложение", "Обновление", LocalConfiguration.current.screenWidthDp <= 460,
        trailing = { Text(s.versionTag, color = TextBody, fontSize = 10.sp) },
    ) {
        StatusRow("Текущая версия") {
            Text("Интерактивный макет", color = TextStrong, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        BackupButton("Проверить обновления", Icons.Outlined.Refresh, a.onCheckUpdates, Modifier.fillMaxWidth())
        Muted(s.updateStatus)
    }
}

@Composable
private fun ControlParams(s: SettingsUiState, a: SettingsActions) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 14.dp).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Text(
            "Параметры контроля продукта", color = ActionText, fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 13.dp).heightIn(min = 28.dp)
                .clickable(role = Role.Button) { open = !open },
        )
        if (open) {
            SettingRow("Интервал взвешиваний", "Через сколько суток показывать напоминание",
                s.interval.toString(), "Интервал взвешивания в сутках", KeyboardType.Number) { t ->
                t.toIntOrNull()?.takeIf { it in 1..30 }?.let(a.onIntervalChange)
            }
            SettingRow("Порог внимания, %/сут", "Зависит от продукта и технологии",
                s.pace.toString(), "Порог внимания в процентах за сутки", KeyboardType.Decimal) { t ->
                t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..20.0 }?.let(a.onPaceChange)
            }
            Text(
                "Это напоминания внутри журнала, не фоновые уведомления. Показания датчиков не считываются.",
                color = TextMuted, fontSize = 10.5.sp, lineHeight = 16.8.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String, hint: String, value: String, description: String,
    keyboard: KeyboardType, onValid: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Label, fontSize = 12.sp)
            Text(hint, color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        val sh = RoundedCornerShape(10.dp)
        BasicTextField(
            value = text,
            onValueChange = { text = it; onValid(it) },
            singleLine = true,
            textStyle = TextStyle(color = TextStrong, fontSize = 14.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Amber),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.width(68.dp).heightIn(min = 44.dp).clip(sh)
                .background(FieldBg).border(1.dp, FieldLine, sh)
                .padding(horizontal = 8.dp, vertical = 11.dp),
        )
    }
}

// ---------------- Окно подтверждения восстановления ----------------

@Composable
fun BackupRestoreSheet(
    source: String, active: Int, archive: Int, logs: Int,
    warning: String = "Текущий журнал будет заменён, а не объединён с копией. Перед заменой сохраним " +
        "страховочную копию для отката. Если сохранить её не получится, замена не будет выполнена.",
    onCancel: () -> Unit, onConfirm: () -> Unit,
) {
    val tiny = LocalConfiguration.current.screenWidthDp <= 355
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp).padding(bottom = 25.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KickerText("Предварительная проверка")
                Text("Восстановить данные?", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight(550))
            }
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Отменить восстановление", tint = TextSoft)
            }
        }
        Text(source, color = TextBody, fontSize = 11.sp, lineHeight = 17.6.sp,
            modifier = Modifier.padding(bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Stat(active, "В работе", Modifier.weight(1f))
            Stat(archive, "В архиве", Modifier.weight(1f))
            Stat(logs, "Взвешиваний", Modifier.weight(1f))
        }
        val ws = RoundedCornerShape(14.dp)
        Text(
            warning, color = Color(0xFFDFC59E), fontSize = 12.sp, lineHeight = 20.4.sp,
            modifier = Modifier.padding(vertical = 15.dp).fillMaxWidth().clip(ws)
                .background(Color(0x08EDBC77)).border(1.dp, Color(0xFF635337), ws).padding(13.dp),
        )
        ButtonPair(tiny, gap = 9.dp) {
            BackupButton("Отмена", null, onCancel, it, minHeight = 45.dp)
            BackupButton("Заменить данные", null, onConfirm, it, main = true, minHeight = 45.dp)
        }
    }
}

// ---------------- Мелкие элементы ----------------

@Composable
private fun width390() = LocalConfiguration.current.screenWidthDp <= 390

@Composable
private fun BackupCard(
    icon: ImageVector, kicker: String, title: String, narrow: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(CardTop, CardBottom)), shape)
            .border(1.dp, CardBorder, shape)
            .padding(if (narrow) 15.dp else 17.dp),
    ) {
        Row(Modifier.padding(bottom = 17.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ish = RoundedCornerShape(12.dp)
            Box(Modifier.size(38.dp).clip(ish).background(ChipBg)
                .border(1.dp, ChipLine, ish), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ChipIcon, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                KickerText(kicker)
                Text(title, color = TextMain, fontSize = if (narrow) 14.sp else 15.sp,
                    lineHeight = if (narrow) 19.6.sp else 21.sp, fontWeight = FontWeight(550))
            }
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun KickerText(text: String) = Text(
    text.uppercase(), color = Kicker, fontSize = 9.sp, letterSpacing = 1.2.sp,
    fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp),
)

@Composable
private fun Body(text: String) = Text(text, color = TextBody, fontSize = 11.5.sp, lineHeight = 19.sp,
    modifier = Modifier.padding(top = 11.dp))

@Composable
private fun Muted(text: String) = Text(text, color = TextMuted, fontSize = 10.5.sp, lineHeight = 16.8.sp,
    modifier = Modifier.padding(top = 11.dp))

@Composable
private fun StatusRow(label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = TextSoft, fontSize = 11.sp, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun ConnectionPill(text: String) {
    val sh = RoundedCornerShape(20.dp)
    Row(Modifier.clip(sh).background(FieldBg).border(1.dp, FieldLine, sh)
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(TextMuted))
        Text(text, color = TextBody, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun CloudChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(sh)
            .background(if (selected) SelectedBg else FieldBg)
            .border(1.dp, if (selected) SelectedLine else Line, sh)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp),
            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = TextMuted))
        Text(text, color = Label, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier = Modifier) {
    val sh = RoundedCornerShape(11.dp)
    Column(modifier.clip(sh).background(FieldBg).border(1.dp, Line, sh)
        .padding(horizontal = 9.dp, vertical = 10.dp)) {
        Text(value.toString(), color = TextStrong, fontSize = 19.sp, lineHeight = 24.7.sp,
            fontWeight = FontWeight.Medium)
        Text(label, color = TextBody, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Две кнопки в ряд, на ширине до 355 dp друг под другом. */
@Composable
private fun ButtonPair(tiny: Boolean, gap: androidx.compose.ui.unit.Dp = 8.dp,
                       content: @Composable (Modifier) -> Unit) {
    Box(Modifier.padding(top = 11.dp)) {
        if (tiny) Column(verticalArrangement = Arrangement.spacedBy(gap)) { content(Modifier.fillMaxWidth()) }
        else Row(horizontalArrangement = Arrangement.spacedBy(gap)) { content(Modifier.weight(1f)) }
    }
}

@Composable
private fun BackupButton(
    text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier,
    main: Boolean = false, enabled: Boolean = true, compact: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val sh = RoundedCornerShape(12.dp)
    val bg = if (main) Accent else BtnSec
    val border = if (main) AccentLine else BtnSecLine
    val fg = if (main) OnAccent else OnSecondary
    Row(
        modifier.alpha(if (enabled) 1f else 0.38f).heightIn(min = minHeight).clip(sh)
            .background(bg).border(BorderStroke(1.dp, border), sh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (compact) 15.dp else 16.dp))
        Text(text, color = fg, fontSize = if (compact) 11.sp else 12.sp, lineHeight = 15.6.sp,
            fontWeight = FontWeight(550), textAlign = TextAlign.Center)
    }
}

@Composable
private fun TextAction(
    text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = ActionText, minHeight: androidx.compose.ui.unit.Dp = 34.dp,
) {
    Row(modifier.heightIn(min = minHeight).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun ThemeCard(s: SettingsUiState, a: SettingsActions, narrow: Boolean) {
    BackupCard(Icons.Outlined.Palette, "Оформление", "Цветовая тема", narrow) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPalettes.forEach { p ->
                ThemeSwatch(p, p.id == s.paletteId, Modifier.weight(1f)) { a.onPaletteChange(p.id) }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(p: AppPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(12.dp)
    Column(
        modifier.heightIn(min = 72.dp).clip(sh)
            .background(p.background)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else p.cardLine, sh)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(p.card, p.accent, p.secondary).forEach {
                Box(Modifier.size(14.dp).clip(CircleShape).background(it))
            }
        }
        Text(p.title, color = p.ink, fontSize = 11.sp)
    }
}

@Composable
private fun ExternalBackupCard(backup: ExternalBackup, narrow: Boolean, tiny: Boolean) {
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) backup.setFolder(uri) }

    val last = backup.lastSuccessMs
    val summary = when {
        backup.running -> "Копируем…"
        backup.folderName == null -> "Папка не выбрана"
        last == 0L -> "Папка: ${backup.folderName}. Копий ещё не было."
        else -> "Папка: ${backup.folderName}. Последняя копия: " +
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(last))
    }

    BackupCard(Icons.Outlined.Folder, "Автокопия", "Ежедневная копия в папку", narrow) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                summary, color = TextBody, fontSize = 11.5.sp, lineHeight = 19.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = backup.enabled,
                onCheckedChange = { on ->
                    if (on && backup.folderName == null) pickFolder.launch(null)
                    else backup.setEnabled(on)
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Accent,
                    checkedThumbColor = OnAccent,
                ),
            )
        }

        backup.error?.let {
            Text(
                it, color = Color(0xFFC58A82), fontSize = 11.sp, lineHeight = 17.6.sp,
                modifier = Modifier.padding(top = 11.dp),
            )
        }

        if (backup.folderName != null) {
            ButtonPair(tiny) {
                BackupButton(
                    "Другая папка", Icons.Outlined.Folder,
                    { pickFolder.launch(null) }, it, compact = narrow,
                )
                BackupButton(
                    "Копия сейчас", Icons.Outlined.FileUpload,
                    { backup.runNow() }, it,
                    enabled = backup.enabled && !backup.running, compact = narrow,
                )
            }
        }

        Muted(
            "Копия делается раз в сутки после сохранения журнала, пока приложение запущено. " +
                "Лучше выбрать облачную папку или карту памяти: копия во внутренней памяти " +
                "не защитит от потери телефона.",
        )
    }
}