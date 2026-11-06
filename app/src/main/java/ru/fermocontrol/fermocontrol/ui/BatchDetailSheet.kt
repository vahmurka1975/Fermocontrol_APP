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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

// Палитра «Design 04» из макета.
private val SheetBg = Color(0xFF162019)
private val Ink = Color(0xFFF0F2E9)
private val Muted = Color(0xFFA1ADA2)
private val Green = Color(0xFFB5D9AB)
private val Amber = Color(0xFFEDBC77)
private val CardBg = Color(0xFF1B271D)
private val CardLine = Color(0xFF33472E)
private val MetricBg = Color(0xFF202D22)
private val MetricLine = Color(0xFF3A4B34)
private val WarnBg = Color(0xFF2A2C20)
private val WarnLine = Color(0xFF6D5738)
private val BtnMain = Color(0xFFBDDCAF)
private val BtnMainText = Color(0xFF192A16)
private val BtnSecBg = Color(0xFF223022)
private val BtnSecLine = Color(0xFF42523B)

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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmArchive by remember { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<PhotoRef?>(null) }

    val metrics = remember(batch) { computeMetrics(batch) }
    val attention = remember(batch, metrics) { batch.requiresAttention(metrics) }
    val logsAsc = remember(batch) { batch.logs.sortedBy { it.atEpochMs } }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
        containerColor = SheetBg,
        contentColor = Ink,
        shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF43513D)) },
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

                SectionTitle("Траектория усушки", "% от стартового веса")
                TrajectoryChart(batch, logsAsc, metrics)

                SectionTitle("Паспорт партии")
                Passport(batch)

                SectionTitle("Фотографии")
                PhotoSection(batch, logsAsc, photoFile) { viewerPhoto = it }

                SectionTitle("История взвешиваний", "${logsAsc.size} зап.")
                WeighHistory(batch, logsAsc, photoFile) { viewerPhoto = it }

                Text(
                    text = "Отметка «Требует проверки» — запись в журнале, " +
                        "а не заключение о безопасности продукта.",
                    color = Color(0xFF94A68A),
                    fontSize = 11.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 15.dp),
                )
            }

            // Неподвижная нижняя панель действий.
            Column(Modifier.fillMaxWidth().background(SheetBg).padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (error != null) {
                    Text(error, color = Amber, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (!batch.archived) {
                        Button(
                            onClick = onWeigh,
                            enabled = !saving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BtnMain,
                                contentColor = BtnMainText,
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 47.dp),
                        ) { Text("Взвесить", fontWeight = FontWeight.SemiBold) }
                    }
                    OutlinedButton(
                        onClick = { confirmArchive = true },
                        enabled = !saving,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BtnSecLine),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BtnSecBg,
                            contentColor = Color(0xFFD5DFCD),
                        ),
                        modifier = Modifier
                            .then(if (batch.archived) Modifier.weight(1f) else Modifier)
                            .heightIn(min = 47.dp),
                    ) { Text(if (batch.archived) "Вернуть из архива" else "В архив") }
                }
            }
        }
    }

    if (confirmArchive) {
        val toArchive = !batch.archived
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            containerColor = Color(0xFF1C291F),
            titleContentColor = Ink,
            textContentColor = Muted,
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
                }) { Text(if (toArchive) "В архив" else "Вернуть", color = Green) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) {
                    Text("Отмена", color = Muted)
                }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "ПАРТИЯ №${batch.number.toString().padStart(2, '0')}",
                color = Color(0xFF9FB390), fontSize = 10.sp, letterSpacing = 1.3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                batch.name, color = Ink, fontSize = 22.sp,
                fontWeight = FontWeight.Medium, lineHeight = 27.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF303E34), RoundedCornerShape(12.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("×", color = Ink, fontSize = 22.sp) }
    }
}

@Composable
private fun DetailIntro(batch: Batch, m: Metrics, attention: Boolean) {
    val (label, color) = when {
        batch.archived -> "В архиве" to Muted
        attention -> "Требует внимания" to Amber
        else -> "Идёт созревание" to Green
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
            color = Muted, fontSize = 11.sp,
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
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = modifier
            .heightIn(min = 110.dp)
            .clip(shape)
            .background(if (warning) WarnBg else MetricBg)
            .border(1.dp, if (warning) WarnLine else MetricLine, shape)
            .padding(13.dp),
    ) {
        Text(label, color = Color(0xFFB1C0A7), fontSize = 10.5.sp)
        Text(
            value,
            color = if (warning) Amber else Color(0xFFD8E8CF),
            fontSize = if (small) 16.sp else 26.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = if (small) 11.dp else 5.dp, bottom = 3.dp),
        )
        Text(sub, color = Color(0xFF92A586), fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ProgressLine(progress: Double, warning: Boolean) {
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF303E32)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                .background(if (warning) Color(0xFFC9B080) else Color(0xFFA5C995)),
        )
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 23.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color(0xFFE0E6D9), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (hint != null) Text(hint, color = Color(0xFF8FA180), fontSize = 10.sp)
    }
}

@Composable
private fun TrajectoryChart(batch: Batch, logsAsc: List<WeightLog>, m: Metrics) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Color(0xFF1C281F)).border(1.dp, Color(0xFF3C5034), shape)
            .padding(14.dp),
    ) {
        if (logsAsc.isEmpty()) {
            Text(
                "График появится после первого взвешивания.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
            )
            return@Column
        }

        val start = batch.startWeightGrams
        val points = listOf(0.0 to 0.0) + logsAsc.map { log ->
            val days = max(0.0, (log.atEpochMs - batch.createdAtEpochMs) / 86_400_000.0)
            val loss = if (start > 0) max(0.0, (start - log.weightGrams) / start * 100.0) else 0.0
            days to loss
        }
        val maxDays = max(1.0, points.maxOf { it.first })
        val maxLoss = max(m.targetLossPercent, points.maxOf { it.second }) * 1.1 + 0.001

        Canvas(Modifier.fillMaxWidth().aspectRatio(540f / 185f)) {
            val w = size.width
            val h = size.height
            fun x(d: Double) = (d / maxDays * w).toFloat()
            fun y(p: Double) = (h - p / maxLoss * h).toFloat()

            // Цель усушки — пунктир.
            drawLine(
                color = Green.copy(alpha = 0.6f),
                start = Offset(0f, y(m.targetLossPercent)),
                end = Offset(w, y(m.targetLossPercent)),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val path = Path()
            points.forEachIndexed { i, (d, p) ->
                if (i == 0) path.moveTo(x(d), y(p)) else path.lineTo(x(d), y(p))
            }
            drawPath(path, Green, style = Stroke(width = 5f))
            points.forEach { (d, p) -> drawCircle(Green, radius = 6f, center = Offset(x(d), y(p))) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Сплошная линия — факт, пунктир — цель ${fmtPct(m.targetLossPercent)}. " +
                "По горизонтали — сутки с закладки (0–${maxDays.roundToLong()}).",
            color = Color(0xFF91A287), fontSize = 10.sp, lineHeight = 16.sp,
        )
    }
}

@Composable
private fun Passport(batch: Batch) {
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
                Text(label, color = Color(0xFFA1B396), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    value.ifBlank { "—" }, color = Color(0xFFE0E9D8), fontSize = 12.sp,
                    textAlign = TextAlign.End, lineHeight = 19.sp, modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF31432C)))
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
                .background(Color(0xFF202C20))
                .border(1.dp, Color(0xFF465A3A), shape)
                .padding(18.dp),
        ) {
            Text("Фотографий пока нет", color = Color(0xFFC4D3B8), fontSize = 12.sp)
            Text(
                "Фото сохраняются вместе с партией и взвешиванием.",
                color = Color(0xFF92A484), fontSize = 11.sp, lineHeight = 17.sp,
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
    val shape = RoundedCornerShape(13.dp)
    val bitmap = rememberPhoto(file, 480)
    Column(
        modifier.clip(shape).background(Color(0xFF202C20))
            .border(1.dp, MetricLine, shape).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(155.dp).background(Color(0xFF142017)), Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, ref.caption, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("Нет файла", color = Muted, fontSize = 11.sp)
            }
        }
        Text(ref.caption, color = Color(0xFFB3C4A7), fontSize = 10.sp,
            lineHeight = 16.sp, modifier = Modifier.padding(9.dp))
    }
}

@Composable
private fun WeighHistory(
    batch: Batch,
    logsAsc: List<WeightLog>,
    photoFile: (String) -> File,
    onOpen: (PhotoRef) -> Unit,
) {
    if (logsAsc.isEmpty()) {
        Text("Взвешиваний ещё не было.", color = Muted, fontSize = 12.sp)
        return
    }
    val shape = RoundedCornerShape(13.dp)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        logsAsc.indices.reversed().forEach { i ->
            val log = logsAsc[i]
            val prev = if (i == 0) batch.startWeightGrams else logsAsc[i - 1].weightGrams
            val delta = log.weightGrams - prev
            Column(
                Modifier.fillMaxWidth().clip(shape).background(CardBg)
                    .border(1.dp, CardLine, shape).padding(13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fmtWeight(log.weightGrams), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${signed(delta)} г к прошлому",
                        color = if (delta > 0) Color(0xFFE0B67D) else Color(0xFFB6D4A5),
                        fontSize = 11.sp,
                    )
                }
                Text(fmtDate(log.atEpochMs), color = Color(0xFF9FAF94), fontSize = 10.sp)
                if (log.smellNote.isNotBlank()) NoteLine("Запах", log.smellNote)
                if (log.surfaceNote.isNotBlank()) NoteLine("Поверхность", log.surfaceNote)
                if (log.needsCheck) {
                    Text(
                        "Требует проверки", color = Color(0xFFE7C797), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp)
                            .border(1.dp, Color(0xFF705E40), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                log.photoFileName?.let { name ->
                    val ref = PhotoRef(name, "Взвешивание · ${fmtDate(log.atEpochMs)}")
                    Text(
                        "Открыть фото", color = Color(0xFFCADCBE), fontSize = 11.sp,
                        modifier = Modifier.padding(top = 9.dp).clip(RoundedCornerShape(9.dp))
                            .border(1.dp, Color(0xFF485D3E), RoundedCornerShape(9.dp))
                            .clickable { onOpen(ref) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteLine(label: String, text: String) {
    Text("$label: $text", color = Color(0xFFA0B192), fontSize = 11.sp,
        lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
}

// ───────────────────────── Полноэкранное фото ─────────────────────────

@Composable
private fun FullscreenPhoto(file: File, caption: String, onClose: () -> Unit) {
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
                Text("Файл фотографии не найден", color = Muted,
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
                caption, color = Color(0xFFDDE5D6), fontSize = 12.sp,
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