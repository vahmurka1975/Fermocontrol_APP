package ru.fermocontrol.fermocontrol.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import ru.fermocontrol.fermocontrol.data.PhotoStore
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.computeMetrics
import ru.fermocontrol.fermocontrol.ui.components.FcPrimaryButton
import ru.fermocontrol.fermocontrol.ui.components.FcSelect
import ru.fermocontrol.fermocontrol.ui.components.FcSheet
import ru.fermocontrol.fermocontrol.ui.components.FieldPair
import ru.fermocontrol.fermocontrol.ui.components.LabeledInput
import ru.fermocontrol.fermocontrol.ui.theme.Amber
import ru.fermocontrol.fermocontrol.ui.theme.AmberLine
import ru.fermocontrol.fermocontrol.ui.theme.AmberSoft
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.fermocontrol.ui.theme.WarnCheckBg
import ru.fermocontrol.fermocontrol.ui.theme.WarnCheckInk
import ru.fermocontrol.fermocontrol.ui.theme.WarnCheckLine
import ru.fermocontrol.fermocontrol.ui.theme.WarnInk

private val SMELLS = listOf(
    "Не указан", "Чистый вяленый аромат", "Пряный, ореховый",
    "Кисловатый", "Резкий, подозрительный",
)
private val SURFACES = listOf(
    "Не указана", "Сухая, без изменений", "Благородный белый налёт",
    "Влажная, липкая", "Уплотнение (риск закала)",
)

private const val MAX_WEIGHT_G = 1_000_000.0
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000.0

private fun weighNum(v: String): Double? =
    v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun weighDec(v: Double, digits: Int): String =
    String.format(Locale.forLanguageTag("ru"), "%.${digits}f", v)

/** Панель «Взвешивание партии» по макету v0.4.1 (#weighModal). */
@Composable
fun WeighSheet(
    batch: Batch,
    saving: Boolean,
    error: String?,
    photoStore: PhotoStore,
    onDismiss: () -> Unit,
    onSave: (
        weightGrams: Double,
        smell: String,
        surface: String,
        needsCheck: Boolean,
        photoUri: Uri?,
    ) -> Unit,
) {
    val p = LocalAppPalette.current
    var weightText by rememberSaveable(batch.id) { mutableStateOf("") }
    var smell by rememberSaveable(batch.id) { mutableStateOf(SMELLS.first()) }
    var surface by rememberSaveable(batch.id) { mutableStateOf(SURFACES.first()) }
    var needsCheck by rememberSaveable(batch.id) { mutableStateOf(false) }
    var photoUri by rememberSaveable(batch.id) { mutableStateOf<Uri?>(null) }
    var cameraUri by rememberSaveable(batch.id) { mutableStateOf<Uri?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked != null) {
            photoUri = picked
            photoError = null
        }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { taken ->
        if (taken) {
            photoUri = cameraUri
            photoError = null
        }
    }

    val preview by produceState<ImageBitmap?>(null, photoUri) {
        value = null
        value = photoUri?.let { uri ->
            try {
                photoStore.loadPreview(uri).asImageBitmap()
            } catch (error: Exception) {
                null
            }
        }
    }

    val now = remember(batch.id) { System.currentTimeMillis() }
    val metrics = remember(batch) { computeMetrics(batch, now) }

    val start = batch.startWeightGrams
    val target = batch.targetLossPercent
    val targetWeight = start * (1.0 - target / 100.0)
    val lastLog = batch.logs.maxByOrNull { it.atEpochMs }
    val prevWeight = lastLog?.weightGrams ?: start
    val prevTime = lastLog?.atEpochMs ?: batch.createdAtEpochMs
    val clockIssue = prevTime > now

    val parsed = weighNum(weightText)
    val entered = parsed?.takeIf { it > 0.0 && it <= MAX_WEIGHT_G }
    val invalidInput = weightText.isNotBlank() && entered == null

    val shown = entered ?: metrics.currentWeightGrams
    val lossPct = if (start > 0.0) (start - shown) / start * 100.0 else 0.0
    val remainG = max(0.0, shown - targetWeight)
    val targetReached = target > 0.0 && lossPct >= target

    val delta = entered?.let { it - prevWeight }
    val elapsed = now - prevTime
    val pace = if (entered != null && start > 0.0 && elapsed >= HOUR_MS) {
        (prevWeight - entered) / start * 100.0 / (elapsed / DAY_MS)
    } else null

    val increased = delta != null && delta > 0.0
    val warning = increased || clockIssue

    val messages = buildList {
        if (invalidInput) add("Введите положительный вес не более 1 000 000 г.")
        if (clockIssue) add("Предыдущая запись датирована будущим. Проверьте часы устройства: новая запись пока не может быть сохранена.")
        if (increased) add("Вес увеличился на ${fmtGrams(delta!!)} г. Проверьте показание, тару и введённые цифры.")
        if (entered != null) {
            if (targetReached) add("Плановая усушка достигнута. Это не подтверждение безопасности и не автоматическая команда завершить процесс.")
            else add("До плановой массы осталось ${fmtGrams(remainG)} г.")
            if (pace == null && !clockIssue) add("При интервале менее часа темп не оценивается; вес и дата сохраняются без подмены интервала.")
        }
    }

    FcSheet(
        title = "Взвешивание партии",
        subtitle = "№${batch.number} · ${batch.name}",
        onDismiss = onDismiss,
        dismissible = !saving,
    ) {
        if (error != null) {
            Text(error, color = p.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        LabeledInput(
            label = "Текущий вес на весах, г",
            value = weightText,
            onValueChange = { weightText = it },
            placeholder = "Прошлый: ${fmtGrams(prevWeight)} г",
            enabled = !saving,
            keyboardType = KeyboardType.Decimal,
            error = if (invalidInput) "Введите вес в граммах, больше 0" else null,
            autoFocus = true,
        )

        // Живой расчёт прогресса
        val box = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(box)
                .background(p.card)
                .border(1.dp, p.cardLine, box)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ЖИВОЙ РАСЧЁТ ПРОГРЕССА", color = p.muted, fontSize = 9.sp,
                    letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (entered != null) "после записи" else "сейчас",
                    color = p.muted, fontSize = 9.sp,
                )
            }
            LiveCell(
                label = "Потеря веса",
                value = fmtPercentChange(lossPct),
                sub = (if (entered != null) "после записи · цель −" else "сейчас · цель −") +
                    weighDec(target, 1) + "%",
            )
            LiveCell(
                label = "До цели",
                value = "${fmtGrams(remainG)} г",
                sub = "до ${fmtGrams(targetWeight)} г",
            )
            LiveCell(
                label = "С прошлого замера",
                value = when {
                    delta != null -> (if (delta > 0.0) "+" else if (delta < 0.0) "−" else "") +
                        fmtGrams(abs(delta)) + " г"
                    weightText.isNotBlank() -> "некорректный вес"
                    else -> "введите вес"
                },
                sub = when {
                    entered == null -> "темп не рассчитан"
                    pace == null -> "темп: интервал менее часа"
                    else -> weighDec(pace, 2) + "%/сут"
                },
                warn = increased,
            )
            if (messages.isNotEmpty()) {
                val amber = warning || invalidInput
                val s = RoundedCornerShape(10.dp)
                Text(
                    text = messages.joinToString(" "),
                    color = if (amber) WarnInk else p.strong,
                    fontSize = 11.sp,
                    lineHeight = 17.6.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(s)
                        .background(if (amber) AmberSoft else p.accent.copy(alpha = 0.09f))
                        .border(1.dp, if (amber) AmberLine else p.accent.copy(alpha = 0.25f), s)
                        .padding(10.dp),
                )
            }
        }

        Text(
            "ОСМОТР И ЗАМЕТКИ · ПО ЖЕЛАНИЮ",
            color = p.label,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        FieldPair(
            first = { m -> FcSelect("Запах", smell, SMELLS, { smell = it }, m) },
            second = { m -> FcSelect("Поверхность", surface, SURFACES, { surface = it }, m) },
        )

        val cbShape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(cbShape)
                .background(WarnCheckBg)
                .border(1.dp, WarnCheckLine, cbShape)
                .toggleable(value = needsCheck, enabled = !saving, role = Role.Checkbox) { needsCheck = it }
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = needsCheck,
                onCheckedChange = null,
                enabled = !saving,
                colors = CheckboxDefaults.colors(
                    checkedColor = Amber,
                    checkmarkColor = p.background,
                    uncheckedColor = p.muted,
                ),
                modifier = Modifier.size(19.dp),
            )
            Column(Modifier.padding(start = 10.dp)) {
                Text("Требует проверки", color = WarnCheckInk, fontSize = 12.sp)
                Text(
                    "Отдельная отметка в журнале, не заключение о безопасности.",
                    color = p.muted, fontSize = 12.sp, lineHeight = 19.sp,
                )
            }
        }

        // Фото взвешивания
        Text(
            "ФОТО · ПО ЖЕЛАНИЮ",
            color = p.label,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PhotoButton("Снять", !saving, Modifier.weight(1f)) {
                try {
                    val uri = photoStore.newCameraUri()
                    cameraUri = uri
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    photoError = "На устройстве нет приложения камеры."
                } catch (e: Exception) {
                    photoError = "Не удалось открыть камеру."
                }
            }
            PhotoButton("Из галереи", !saving, Modifier.weight(1f)) {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }

        if (photoUri != null) {
            val pShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(pShape)
                    .background(p.card)
                    .border(1.dp, p.cardLine, pShape)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.field),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = preview
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = "Фото взвешивания",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Фото приложено", color = p.strong, fontSize = 12.sp)
                    Text(
                        "Сохранится вместе с записью.",
                        color = p.muted, fontSize = 11.sp, lineHeight = 17.sp,
                    )
                }
                TextButton(onClick = { photoUri = null }, enabled = !saving) {
                    Text("Убрать", color = p.muted)
                }
            }
        }

        photoError?.let {
            Text(it, color = p.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
        }

        Spacer(Modifier.height(4.dp))

        FcPrimaryButton(
            text = if (saving) "Сохранение…" else "Сохранить запись",
            enabled = !saving && entered != null && !clockIssue,
        ) {
            entered?.let {
                onSave(
                    it,
                    smell.takeUnless { s -> s == SMELLS.first() }.orEmpty(),
                    surface.takeUnless { s -> s == SURFACES.first() }.orEmpty(),
                    needsCheck,
                    photoUri,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PhotoButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = LocalAppPalette.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, p.secondaryLine),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.secondary,
            contentColor = p.onSecondary,
        ),
        modifier = modifier,
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
private fun LiveCell(label: String, value: String, sub: String, warn: Boolean = false) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.field)
            .border(1.dp, if (warn) AmberLine else p.divider, shape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(label, color = p.muted, fontSize = 10.sp)
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(value, color = if (warn) Amber else p.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = p.muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}