@file:OptIn(ExperimentalMaterial3Api::class)

package ru.fermocontrol.fermocontrol.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.Metrics
import ru.fermocontrol.fermocontrol.model.WeightLog
import ru.fermocontrol.fermocontrol.model.computeMetrics
import ru.fermocontrol.fermocontrol.model.requiresAttention
import ru.fermocontrol.fermocontrol.ui.theme.Amber
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.fermocontrol.ui.theme.WarnDelta
import ru.fermocontrol.fermocontrol.ui.theme.WarnFill
import ru.fermocontrol.fermocontrol.ui.theme.WarnLine
import ru.fermocontrol.fermocontrol.ui.theme.WarnPanel
import ru.fermocontrol.fermocontrol.ui.theme.WarnPillInk
import ru.fermocontrol.fermocontrol.ui.theme.WarnPillLine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private val RU: Locale = Locale.forLanguageTag("ru-RU")

private data class PhotoRef(val fileName: String, val caption: String)

@Composable
fun BatchDetailSheet(
    batch: Batch,
    saving: Boolean,
    error: String?,
    photoFile: (String) -> File,
    onDismiss: () -> Unit,
    onWeigh: () -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDeleteLog: (WeightLog) -> Unit,
    onDeleteBatch: () -> Unit,
) {
    val p = LocalAppPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmArchive by remember { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<PhotoRef?>(null) }
    var logToDelete by remember { mutableStateOf<WeightLog?>(null) }
    var confirmDeleteBatch by remember { mutableStateOf(false) }

    val metrics = remember(batch) { computeMetrics(batch) }
    val attention = remember(batch, metrics) { batch.requiresAttention(metrics) }
    val logsAsc = remember(batch) { batch.logs.sortedBy { it.atEpochMs } }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
        containerColor = p.sheet,
        contentColor = p.ink,
        shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.chipLine) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                DetailHeader(batch, onClose = { if (!saving) onDismiss() })
                DetailIntro(batch, metrics, attention)
                MetricsGrid(batch, metrics)
                ProgressLine(metrics.progress, attention)

                TrajectoryChart(batch, logsAsc, metrics)

                SectionTitle("Паспорт партии")
                Passport(batch)
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !saving,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, p.secondaryLine),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = p.secondary,
                        contentColor = p.onSecondary,
                    ),
                    modifier = Modifier.padding(top = 12.dp).heightIn(min = 44.dp),
                ) { Text("Редактировать паспорт") }

                SectionTitle("Фотографии")
                PhotoSection(batch, logsAsc, photoFile) { viewerPhoto = it }

                SectionTitle("История взвешиваний", "${logsAsc.size} зап.")
                WeighHistory(batch, logsAsc, !saving, { logToDelete = it }) { viewerPhoto = it }

                Text(
                    text = "Отметка «Требует проверки» — запись в журнале, " +
                        "а не заключение о безопасности продукта.",
                    color = p.kicker,
                    fontSize = 11.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 15.dp),
                )
            }

            // Неподвижная нижняя панель действий.
            Column(Modifier.fillMaxWidth().background(p.sheet).padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (error != null) {
                    Text(error, color = Amber, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (batch.archived) {
                        OutlinedButton(
                            onClick = { confirmDeleteBatch = true },
                            enabled = !saving,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, p.danger.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = p.danger,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 47.dp),
                        ) { Text("Удалить партию", fontSize = 13.sp, maxLines = 1) }
                    } else {
                        Button(
                            onClick = onWeigh,
                            enabled = !saving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = p.accent,
                                contentColor = p.onAccent,
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 47.dp),
                        ) { Text("Взвесить", fontWeight = FontWeight.SemiBold) }
                    }
                    OutlinedButton(
                        onClick = { confirmArchive = true },
                        enabled = !saving,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, p.secondaryLine),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = p.secondary,
                            contentColor = p.onSecondary,
                        ),
                        contentPadding = if (batch.archived) PaddingValues(horizontal = 8.dp)
                        else ButtonDefaults.ContentPadding,
                        modifier = (if (batch.archived) Modifier.weight(1f) else Modifier)
                            .heightIn(min = 47.dp),
                    ) {
                        Text(
                            if (batch.archived) "Вернуть из архива" else "В архив",
                            fontSize = if (batch.archived) 13.sp else 14.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (confirmArchive) {
        val toArchive = !batch.archived
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            containerColor = p.card,
            titleContentColor = p.ink,
            textContentColor = p.muted,
            title = { Text(if (toArchive) "Отправить в архив?" else "Вернуть из архива?") },
            text = {
                Text(
                    if (toArchive)
                        "Партия останется в журнале, но взвешивать её будет нельзя. " +
                            "Вернуть её можно в любой момент."
                    else
                        "Партия снова появится среди активных, и её можно будет взвешивать.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    onSetArchived(toArchive)
                }) { Text(if (toArchive) "В архив" else "Вернуть", color = p.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) {
                    Text("Отмена", color = p.muted)
                }
            },
        )
    }

    logToDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            containerColor = p.card,
            titleContentColor = p.ink,
            textContentColor = p.muted,
            title = { Text("Удалить без возврата?") },
            text = {
                Text(
                    "Взвешивание ${fmtWeight(log.weightGrams)} от ${fmtDate(log.atEpochMs)} " +
                        "будет удалено вместе с фото. Восстановить запись нельзя.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    logToDelete = null
                    onDeleteLog(log)
                }) { Text("Удалить", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) { Text("Отмена", color = p.muted) }
            },
        )
    }

    if (confirmDeleteBatch) {
        AlertDialog(
            onDismissRequest = { confirmDeleteBatch = false },
            containerColor = p.card,
            titleContentColor = p.ink,
            textContentColor = p.muted,
            title = { Text("Удалить без возврата?") },
            text = {
                Text(
                    "Партия №${batch.number} «${batch.name}» будет удалена вместе со всеми " +
                        "взвешиваниями и фотографиями. Восстановить её нельзя.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteBatch = false
                    onDeleteBatch()
                }) { Text("Удалить", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteBatch = false }) { Text("Отмена", color = p.muted) }
            },
        )
    }

    viewerPhoto?.let { ref ->
        FullscreenPhoto(
            file = photoFile(ref.fileName),
            caption = ref.caption,
            onClose = { viewerPhoto = null },
        )
    }
}

// ───────────────────────── Блоки экрана ─────────────────────────

@Composable
private fun DetailHeader(batch: Batch, onClose: () -> Unit) {
    val p = LocalAppPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "ПАРТИЯ №${batch.number.toString().padStart(2, '0')}",
                color = p.kicker, fontSize = 10.sp, letterSpacing = 1.3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                batch.name, color = p.ink, fontSize = 22.sp,
                fontWeight = FontWeight.Medium, lineHeight = 27.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, p.divider, RoundedCornerShape(12.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("×", color = p.ink, fontSize = 22.sp) }
    }
}

@Composable
private fun DetailIntro(batch: Batch, m: Metrics, attention: Boolean) {
    val p = LocalAppPalette.current
    val (label, color) = when {
        batch.archived -> "В архиве" to p.muted
        attention -> "Требует внимания" to Amber
        else -> "Идёт созревание" to p.accent
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label, color = color, fontSize = 10.sp,
            modifier = Modifier
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        Text(
            "Закладка ${fmtDay(batch.createdAtEpochMs)} · день ${m.daysElapsed}",
            color = p.muted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun MetricsGrid(batch: Batch, m: Metrics) {
    val targetReached = m.targetLossPercent > 0 && m.lostPercent >= m.targetLossPercent
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard(
                "Текущий вес", fmtWeight(m.currentWeightGrams),
                "старт ${fmtWeight(m.startWeightGrams)}", Modifier.weight(1f),
            )
            MetricCard(
                "Усушка", fmtPct(m.lostPercent),
                "цель ${fmtPct(m.targetLossPercent)} · −${fmtWeight(m.lostGrams)}",
                Modifier.weight(1f), warning = targetReached,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard(
                "До цели", if (targetReached) "достигнута" else fmtPct(m.toTargetPercent),
                if (targetReached) "цель усушки по весу" else "ещё ${fmtWeight(m.toTargetGrams)}",
                Modifier.weight(1f), small = targetReached,
            )
            MetricCard(
                "Прогноз",
                m.estimatedDaysLeft?.let { "≈ $it дн." } ?: "—",
                m.pacePercentPerDay?.let { "темп ${fmtPct(it, 2)}/сут" }
                    ?: "нужно взвешивание через сутки и позже",
                Modifier.weight(1f), small = m.estimatedDaysLeft == null,
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier,
    warning: Boolean = false,
    small: Boolean = false,
) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = modifier
            .heightIn(min = 110.dp)
            .clip(shape)
            .background(if (warning) WarnPanel else p.card)
            .border(1.dp, if (warning) WarnLine else p.cardLine, shape)
            .padding(13.dp),
    ) {
        Text(label, color = p.soft, fontSize = 10.5.sp)
        Text(
            value,
            color = if (warning) Amber else p.strong,
            fontSize = if (small) 16.sp else 26.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = if (small) 11.dp else 5.dp, bottom = 3.dp),
        )
        Text(sub, color = p.muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ProgressLine(progress: Double, warning: Boolean) {
    val p = LocalAppPalette.current
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(p.divider),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                .background(if (warning) WarnFill else p.accent),
        )
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    val p = LocalAppPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 23.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (hint != null) Text(hint, color = p.muted, fontSize = 10.sp)
    }
}

// ───────────────────────── График «Динамика массы» ─────────────────────────
// Повторяет trajectoryForecastDay / buildTrajectorySvg / buildTrajectoryChart из макета v0.4.1.
// Размеры в dp соответствуют px макета.

private const val CHART_DAY_MS = 86_400_000.0

private data class ChartPoint(val day: Double, val weight: Double)

private class ChartModel(
    val points: List<ChartPoint>,
    val targetWeight: Double,
    val targetPct: Double,
    val forecastDay: Double?,
    val etaDate: String?,
    val currentDay: Double,
    val measuredDay: Double,
    val totalDays: Int,
    val lastLogMs: Long?,
    val archived: Boolean,
    val reached: Boolean,
    val fast: Boolean,
    val reason: String,
)

private fun buildChartModel(batch: Batch, logsAsc: List<WeightLog>, m: Metrics, nowMs: Long): ChartModel {
    val start = batch.startWeightGrams
    val created = batch.createdAtEpochMs
    val last = logsAsc.lastOrNull()
    val prev = logsAsc.getOrNull(logsAsc.size - 2)
    val prevMs = prev?.atEpochMs ?: created
    val prevW = prev?.weightGrams ?: start
    val current = last?.weightGrams ?: start
    val lastMs = last?.atEpochMs ?: created
    val archived = batch.archived
    val endMs = if (archived) lastMs else nowMs
    val totalDays = max(0L, floor((endMs - created) / CHART_DAY_MS).toLong()).toInt()
    val targetWeight = Math.round(start * (1 - batch.targetLossPercent / 100.0) * 10.0) / 10.0
    val remain = max(0.0, Math.round((current - targetWeight) * 10.0) / 10.0)

    val segDays = (lastMs - prevMs) / CHART_DAY_MS
    val pace: Double? = if (last != null && segDays >= 1.0 / 24 && start > 0) {
        (-(current - prevW) / start * 100.0) / segDays
    } else null

    // Пороги макета по умолчанию: напоминание о взвешивании — 3 сут, быстрая усушка — 1,5 %/сут.
    val clock = !archived && lastMs > nowMs
    val daysSince = max(0L, floor((nowMs - lastMs) / CHART_DAY_MS).toLong())
    val due = !archived && !clock && daysSince >= 3
    val fast = !archived && pace != null && pace > 1.5
    val gain = !archived && last != null && current > prevW
    val inspection = !archived && last?.needsCheck == true
    val reached = m.targetLossPercent > 0.0 && m.lostPercent >= m.targetLossPercent

    var etaDate: String? = null
    var forecastDay: Double? = null
    if (!archived && !due && !fast && !clock && !inspection && remain > 0 && pace != null && pace > 0) {
        val perDay = pace * start / 100.0
        val projectedMs = lastMs + remain / perDay * CHART_DAY_MS
        val remainingDays = ceil((projectedMs - nowMs) / CHART_DAY_MS)
        if (remainingDays > 0 && remainingDays < 366) {
            etaDate = fmtShortDate(projectedMs.toLong())
            val day = max(0.0, (lastMs - created) / CHART_DAY_MS) + remain / perDay
            if (day.isFinite()) forecastDay = day
        }
    }

    val reason = when {
        clock -> "Проверьте часы и дату замера"
        inspection -> "Сначала проверьте наблюдения"
        fast -> "Сначала подтвердите темп свежим замером"
        due -> "Нужен свежий замер"
        gain -> "Проверьте увеличение веса"
        last == null -> "Добавьте первое взвешивание"
        else -> "Нужны замеры со снижением веса и интервалом не менее часа"
    }
    val points = listOf(ChartPoint(0.0, start)) + logsAsc.map {
        ChartPoint(max(0.0, (it.atEpochMs - created) / CHART_DAY_MS), it.weightGrams)
    }
    return ChartModel(
        points = points, targetWeight = targetWeight, targetPct = batch.targetLossPercent,
        forecastDay = forecastDay, etaDate = etaDate,
        currentDay = max(0.0, (endMs - created) / CHART_DAY_MS),
        measuredDay = max(0.0, (lastMs - created) / CHART_DAY_MS),
        totalDays = totalDays, lastLogMs = last?.atEpochMs, archived = archived,
        reached = reached, fast = fast, reason = reason,
    )
}

@Composable
private fun TrajectoryChart(batch: Batch, logsAsc: List<WeightLog>, m: Metrics) {
    val p = LocalAppPalette.current
    val nowMs = remember(batch) { System.currentTimeMillis() }
    val c = remember(batch, logsAsc, m, nowMs) { buildChartModel(batch, logsAsc, m, nowMs) }
    val narrow = LocalConfiguration.current.screenWidthDp <= 400
    val forecast = c.forecastDay

    val status = when {
        c.archived -> "Партия в архиве"
        c.reached -> "Плановая усушка достигнута"
        forecast != null -> "Ориентир: ~" + c.etaDate
        else -> c.reason
    }
    val finishLabel = when {
        c.archived -> "Завершение партии"
        c.reached -> "Цель по массе"
        else -> "Расчётный финиш"
    }
    val finishValue = when {
        c.archived -> "День ${c.totalDays}"
        c.reached -> "Достигнута"
        forecast != null -> "~День ${ceil(forecast).toLong()}"
        else -> "Пока не рассчитан"
    }
    val finishNote = when {
        c.archived -> c.lastLogMs?.let { fmtShortDate(it) } ?: fmtShortDate(batch.createdAtEpochMs)
        c.reached -> "Это не подтверждение безопасности"
        forecast != null -> "~${c.etaDate} · при сохранении темпа"
        else -> c.reason
    }
    val measuredNote = c.lastLogMs
        ?.let { "Замер: ${fmtShortDate(it)} · день ${fmtOne(c.measuredDay)}" }
        ?: "Пока записан только начальный вес"

    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(top = 20.dp).fillMaxWidth().clip(shape)
            .background(p.card).border(1.dp, p.cardLine, shape)
            .padding(14.dp),
    ) {
        // Заголовок и статус.
        val etaColor = if (c.fast) Amber else p.accent
        if (narrow) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text("ДИНАМИКА МАССЫ", color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                Text(status, color = etaColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 17.6.sp, modifier = Modifier.padding(top = 5.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), Arrangement.SpaceBetween, Alignment.Top) {
                Text("ДИНАМИКА МАССЫ", color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                Text(status, color = etaColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 17.6.sp, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp))
            }
        }

        // Полоса «Цель по массе».
        val stripShape = RoundedCornerShape(10.dp)
        Row(
            Modifier.padding(top = 4.dp, bottom = 12.dp).fillMaxWidth().clip(stripShape)
                .border(1.dp, p.accent.copy(alpha = 0.25f), stripShape)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Text("Цель по массе", color = p.soft, fontSize = 11.sp)
            Text("−${fmtPct(c.targetPct).removeSuffix("%")}% · ${fmtGramsLocal(c.targetWeight)} г",
                color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp))
        }

        // Подписи осей.
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), Arrangement.SpaceBetween) {
            Text("Масса, г", color = p.muted, fontSize = 10.sp)
            Text("Дни от закладки", color = p.muted, fontSize = 10.sp)
        }

        TrajectoryCanvas(c)

        // Вехи: текущий день и расчётный финиш.
        val m1: @Composable (Modifier) -> Unit = {
            Milestone(if (c.archived) "На момент завершения" else "Текущий день · сегодня",
                "День ${c.totalDays}", measuredNote, false, it)
        }
        val m2: @Composable (Modifier) -> Unit = {
            Milestone(finishLabel, finishValue, finishNote, forecast != null, it)
        }
        if (narrow) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                m1(Modifier.fillMaxWidth()); m2(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m1(Modifier.weight(1f).fillMaxHeight()); m2(Modifier.weight(1f).fillMaxHeight())
            }
        }

        ChartLegend(showForecast = forecast != null, showToday = !c.archived)

        Text(
            (if (forecast != null)
                "Прогноз считается от даты последнего замера по темпу последнего интервала. Темп может меняться; дата приблизительная."
            else "Пунктирный прогноз не строится без подходящих свежих замеров.") +
                " День «сегодня» не означает, что продукт взвешен сегодня.",
            color = p.kicker, fontSize = 10.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 11.dp),
        )
    }
}

@Composable
private fun Milestone(label: String, value: String, note: String, forecast: Boolean, modifier: Modifier) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(11.dp)
    Column(modifier.clip(shape).background(p.field).border(1.dp, p.divider, shape).padding(11.dp)) {
        Text(label, color = p.muted, fontSize = 10.sp)
        Text(value, color = if (forecast) Amber else p.ink, fontSize = 16.sp,
            fontWeight = FontWeight.Medium, lineHeight = 22.4.sp, modifier = Modifier.padding(top = 4.dp))
        Text(note, color = p.body, fontSize = 10.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(showForecast: Boolean, showToday: Boolean) {
    val p = LocalAppPalette.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendItem("Замеры", p.accent, null, 2.5f)
        if (showForecast) LegendItem("Прогноз", Amber, floatArrayOf(4f, 4f), 2.5f)
        LegendItem("Целевой вес", p.accent.copy(alpha = 0.7f), floatArrayOf(2f, 3f), 2.5f)
        if (showToday) LegendItem("Сегодня", p.muted, floatArrayOf(2f, 3f), 2.5f)
    }
}

@Composable
private fun LegendItem(text: String, color: Color, dash: FloatArray?, thickness: Float) {
    val p = LocalAppPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(16.dp, 3.dp)) {
            val d = density
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = thickness * d,
                pathEffect = dash?.let { PathEffect.dashPathEffect(floatArrayOf(it[0] * d, it[1] * d)) },
            )
        }
        Text(text, color = p.muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun TrajectoryCanvas(c: ChartModel) {
    val p = LocalAppPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth.value.toDouble()
        val h = if (w < 380) 210.0 else 225.0
        val tickCount = if (w < 400) 3 else 5
        val lastPt = c.points.last()
        val maxExtent = max(lastPt.day, max(c.currentDay, c.forecastDay ?: 0.0))
        val step = max(1, ceil(max(7.0, maxExtent * 1.1) / (tickCount - 1)).toInt())
        val maxDay = (step * (tickCount - 1)).toDouble()
        val weights = c.points.map { it.weight }
        val lowest = min(c.targetWeight, weights.minOf { it })
        val highest = max(c.targetWeight, weights.maxOf { it })
        val margin = max(1.0, max((highest - lowest) * 0.10, highest * 0.025))
        val minW = max(0.0, lowest - margin)
        val maxW = highest + margin
        val yWeights = listOf(maxW, (maxW + minW) / 2, minW)
        val labelLength = yWeights.maxOf { fmtGramsLocal(it).length }
        val padL = min(100.0, max(48.0, ceil(labelLength * 6.6 + 11)))
        val padR = 12.0
        val padT = 15.0
        val padB = 30.0
        val plotW = w - padL - padR
        val plotH = h - padT - padB

        Canvas(Modifier.fillMaxWidth().height(h.dp)) {
            val d = density
            fun px(v: Double) = (v * d).toFloat()
            fun xOf(day: Double) = px(padL + day / maxDay * plotW)
            fun yOf(wt: Double) = px(padT + (maxW - wt) / max(1.0, maxW - minW) * plotH)
            fun label(s: String, x: Float, y: Float, align: android.graphics.Paint.Align) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = p.muted.toArgb(); textSize = 11f * d; textAlign = align
                }
                drawIntoCanvas { it.nativeCanvas.drawText(s, x, y, paint) }
            }
            val bottom = px(padT + plotH)
            val right = px(w - padR)

            // Сетка и подписи массы.
            yWeights.forEach { wt ->
                val y = yOf(wt)
                drawLine(p.divider, Offset(px(padL), y), Offset(right, y), strokeWidth = d)
                label(fmtGramsLocal(wt), px(padL - 8), y + 4f * d, android.graphics.Paint.Align.RIGHT)
            }
            // Линия «сегодня».
            if (!c.archived) {
                val x = xOf(c.currentDay)
                drawLine(p.muted.copy(alpha = 0.45f), Offset(x, px(padT)), Offset(x, bottom), strokeWidth = d,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * d, 4f * d)))
            }
            // Заливка под линией.
            val area = Path().apply {
                moveTo(px(padL), bottom)
                c.points.forEach { lineTo(xOf(it.day), yOf(it.weight)) }
                lineTo(xOf(lastPt.day), bottom)
                close()
            }
            val top = c.points.minOf { yOf(it.weight) }
            drawPath(area, Brush.verticalGradient(
                listOf(p.accent.copy(alpha = 0.16f), p.accent.copy(alpha = 0f)),
                startY = top, endY = max(bottom, top + 1f)))
            // Целевой вес.
            val yTarget = yOf(c.targetWeight)
            drawLine(p.accent.copy(alpha = 0.65f), Offset(px(padL), yTarget), Offset(right, yTarget),
                strokeWidth = 1.4f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * d, 4f * d)))
            // Факт.
            val line = Path().apply {
                c.points.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(xOf(pt.day), yOf(pt.weight)) else lineTo(xOf(pt.day), yOf(pt.weight))
                }
            }
            drawPath(line, p.accent, style = Stroke(width = 2.3f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Прогноз.
            c.forecastDay?.let { fd ->
                drawLine(Amber, Offset(xOf(lastPt.day), yOf(lastPt.weight)), Offset(xOf(fd), yTarget),
                    strokeWidth = 2f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * d, 5f * d)))
                drawCircle(p.card, 4f * d, Offset(xOf(fd), yTarget))
                drawCircle(Amber, 4f * d, Offset(xOf(fd), yTarget), style = Stroke(2f * d))
            }
            // Точки замеров.
            c.points.forEachIndexed { i, pt ->
                val isLast = i == c.points.lastIndex
                if (c.points.size > 80 && i != 0 && !isLast) return@forEachIndexed
                val r = (if (isLast) 4f else 2.8f) * d
                val ctr = Offset(xOf(pt.day), yOf(pt.weight))
                drawCircle(if (isLast) p.accent else p.card, r, ctr)
                drawCircle(p.accent, r, ctr, style = Stroke(1.8f * d))
            }
            // Подписи дней.
            for (i in 0 until tickCount) {
                val align = when (i) {
                    0 -> android.graphics.Paint.Align.LEFT
                    tickCount - 1 -> android.graphics.Paint.Align.RIGHT
                    else -> android.graphics.Paint.Align.CENTER
                }
                label((i * step).toString(), xOf((i * step).toDouble()), px(h - 10), align)
            }
        }
    }
}

private fun fmtGramsLocal(v: Double): String =
    String.format(RU, "%,.${if (v == floor(v)) 0 else 1}f", v)
private fun fmtOne(v: Double): String = String.format(RU, "%.1f", v)
private fun fmtShortDate(ms: Long): String = SimpleDateFormat("d MMM", RU).format(Date(ms))

@Composable
private fun Passport(batch: Batch) {
    val p = LocalAppPalette.current
    val rows = listOf(
        "Категория" to batch.category,
        "Сырьё" to batch.rawMaterial,
        "Посол и специи" to batch.spices,
        "Оболочка" to batch.casing,
        "Температура" to (batch.temperatureC?.let { "${fmtNum(it)} °C" } ?: ""),
        "Влажность" to (batch.humidityPercent?.let { "${fmtNum(it)} %" } ?: ""),
        "Заметка" to batch.note,
    )
    Column {
        rows.forEach { (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(label, color = p.body, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    value.ifBlank { "—" }, color = p.ink, fontSize = 12.sp,
                    textAlign = TextAlign.End, lineHeight = 19.sp, modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
        }
    }
}

@Composable
private fun PhotoSection(
    batch: Batch,
    logsAsc: List<WeightLog>,
    photoFile: (String) -> File,
    onOpen: (PhotoRef) -> Unit,
) {
    val p = LocalAppPalette.current
    val photos = buildList {
        batch.photoFileName?.let { add(PhotoRef(it, "Исходное фото · ${fmtDay(batch.createdAtEpochMs)}")) }
        logsAsc.forEach { log ->
            log.photoFileName?.let { add(PhotoRef(it, "Взвешивание · ${fmtDate(log.atEpochMs)}")) }
        }
    }
    if (photos.isEmpty()) {
        val shape = RoundedCornerShape(15.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(p.card)
                .border(1.dp, p.cardLine, shape)
                .padding(18.dp),
        ) {
            Text("Фотографий пока нет", color = p.strong, fontSize = 12.sp)
            Text(
                "Фото сохраняются вместе с партией и взвешиванием.",
                color = p.muted, fontSize = 11.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { ref ->
                    PhotoTile(ref, photoFile(ref.fileName), Modifier.weight(1f)) { onOpen(ref) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhotoTile(ref: PhotoRef, file: File, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(13.dp)
    val bitmap = rememberPhoto(file, 480)
    Column(
        modifier.clip(shape).background(p.card)
            .border(1.dp, p.cardLine, shape).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(155.dp).background(p.field), Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, ref.caption, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("Нет файла", color = p.muted, fontSize = 11.sp)
            }
        }
        Text(ref.caption, color = p.soft, fontSize = 10.sp,
            lineHeight = 16.sp, modifier = Modifier.padding(9.dp))
    }
}

@Composable
private fun WeighHistory(
    batch: Batch,
    logsAsc: List<WeightLog>,
    enabled: Boolean,
    onDelete: (WeightLog) -> Unit,
    onOpen: (PhotoRef) -> Unit,
) {
    val p = LocalAppPalette.current
    if (logsAsc.isEmpty()) {
        Text("Взвешиваний ещё не было.", color = p.muted, fontSize = 12.sp)
        return
    }
    val shape = RoundedCornerShape(13.dp)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        logsAsc.indices.reversed().forEach { i ->
            val log = logsAsc[i]
            val prev = if (i == 0) batch.startWeightGrams else logsAsc[i - 1].weightGrams
            val delta = log.weightGrams - prev
            Column(
                Modifier.fillMaxWidth().clip(shape).background(p.card)
                    .border(1.dp, p.cardLine, shape).padding(13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fmtWeight(log.weightGrams), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${signed(delta)} г к прошлому",
                        color = if (delta > 0) WarnDelta else p.accent,
                        fontSize = 11.sp,
                    )
                }
                Text(fmtDate(log.atEpochMs), color = p.body, fontSize = 10.sp)
                if (log.smellNote.isNotBlank()) NoteLine("Запах", log.smellNote)
                if (log.surfaceNote.isNotBlank()) NoteLine("Поверхность", log.surfaceNote)
                if (log.needsCheck) {
                    Text(
                        "Требует проверки", color = WarnPillInk, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp)
                            .border(1.dp, WarnPillLine, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    log.photoFileName?.let { name ->
                        HistoryAction("Открыть фото", p.action, p.chipLine, true) {
                            onOpen(PhotoRef(name, "Взвешивание · ${fmtDate(log.atEpochMs)}"))
                        }
                    }
                    HistoryAction("Удалить запись", p.danger, p.danger.copy(alpha = 0.5f), enabled) {
                        onDelete(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryAction(
    text: String,
    ink: Color,
    line: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Text(
        text,
        color = if (enabled) ink else ink.copy(alpha = 0.4f),
        fontSize = 11.sp,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun NoteLine(label: String, text: String) {
    val p = LocalAppPalette.current
    Text("$label: $text", color = p.body, fontSize = 11.sp,
        lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
}

// ───────────────────────── Полноэкранное фото ─────────────────────────

@Composable
private fun FullscreenPhoto(file: File, caption: String, onClose: () -> Unit) {
    val p = LocalAppPalette.current
    val bitmap = rememberPhoto(file, 2400)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y,
                        ),
                )
            } else {
                Text("Файл фотографии не найден", color = p.muted,
                    modifier = Modifier.align(Alignment.Center))
            }
            Text(
                "×", color = Color.White, fontSize = 26.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .size(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0x99000000)).clickable(onClick = onClose)
                    .padding(top = 5.dp),
            )
            Text(
                caption, color = p.strong, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

@Composable
private fun rememberPhoto(file: File, maxSide: Int): ImageBitmap? =
    produceState<ImageBitmap?>(null, file, maxSide) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxSide) }
    }.value

private fun decodeSampled(file: File, maxSide: Int): ImageBitmap? {
    if (!file.exists()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// ───────────────────────── Форматирование ─────────────────────────

private fun fmtWeight(v: Double) = String.format(RU, "%,d", v.roundToLong()) + " г"
private fun fmtPct(v: Double, digits: Int = 1) = String.format(RU, "%.${digits}f", v) + "%"
private fun fmtNum(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(RU, "%.1f", v)
private fun signed(d: Double): String {
    val n = String.format(RU, "%,d", abs(d).roundToLong())
    return when {
        d > 0.5 -> "+$n"
        d < -0.5 -> "−$n"
        else -> n
    }
}
private fun fmtDate(ms: Long) = SimpleDateFormat("d MMM yyyy, HH:mm", RU).format(Date(ms))
private fun fmtDay(ms: Long) = SimpleDateFormat("d MMM yyyy", RU).format(Date(ms))