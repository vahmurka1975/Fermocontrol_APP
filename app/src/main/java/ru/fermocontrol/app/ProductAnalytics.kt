package ru.fermocontrol.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val DAY_MILLIS = 86_400_000L
const val WEIGHING_REMINDER_DAYS = 3L
const val WEIGHING_REMINDER_MILLIS = WEIGHING_REMINDER_DAYS * DAY_MILLIS

data class ChartPoint(
    val at: Long,
    val weight: Double,
    val label: String,
    val isInitial: Boolean = false
)

data class DryingStats(
    val elapsedDays: Double,
    val absoluteLossGrams: Double,
    val lossPercent: Double,
    val remainingGramsToTarget: Double,
    val remainingPercentToTarget: Double,
    val avgGramsPerDay: Double?,
    val avgPercentPerDay: Double?
)

fun lastWeighingOrStartTime(product: Product): Long =
    product.weighings.maxOfOrNull { it.at } ?: product.startedAt

fun daysSince(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): Long =
    ((nowMs - timestampMs).coerceAtLeast(0L)) / DAY_MILLIS

fun isTargetReached(product: Product): Boolean =
    product.archivedAt == null && product.lossPercent >= product.targetLoss

fun needsWeighing(
    product: Product,
    nowMs: Long = System.currentTimeMillis(),
    thresholdMs: Long = product.reminderIntervalDays.coerceIn(1, 60) * DAY_MILLIS
): Boolean {
    if (product.archivedAt != null) return false
    val referenceTime = lastWeighingOrStartTime(product)
    return (nowMs - referenceTime) >= thresholdMs
}

fun weighingStatusText(product: Product, nowMs: Long = System.currentTimeMillis()): String {
    if (product.archivedAt != null) {
        return if (product.weighings.isEmpty()) {
            "Записей взвешивания нет"
        } else {
            "Взвешиваний в журнале: ${product.weighings.size}"
        }
    }
    if (product.weighings.isEmpty()) {
        val d = daysSince(product.startedAt, nowMs)
        return if (d == 0L) {
            "Ещё не взвешивался · заложен сегодня"
        } else {
            "Ещё не взвешивался · прошло $d дн."
        }
    }
    val lastAt = product.weighings.maxOf { it.at }
    val d = daysSince(lastAt, nowMs)
    return if (d == 0L) {
        "Взвешен сегодня (${dateTime(lastAt)})"
    } else {
        "Последнее взвешивание: $d дн. назад (${dateTime(lastAt)})"
    }
}

fun matchesProductQuery(product: Product, rawQuery: String): Boolean {
    val q = rawQuery.trim().lowercase()
    if (q.isEmpty()) return true
    val cleanNumberQuery = q.removePrefix("№").removePrefix("#").trim()
    if (cleanNumberQuery.isNotEmpty() && product.number.toString() == cleanNumberQuery) {
        return true
    }
    return product.name.lowercase().contains(q) ||
        "№${product.number}".contains(q) ||
        product.basis.lowercase().contains(q) ||
        product.casing.lowercase().contains(q) ||
        product.spices.any { it.lowercase().contains(q) }
}

fun buildChartPoints(product: Product): List<ChartPoint> {
    val sorted = product.weighings.sortedBy { it.at }
    val result = ArrayList<ChartPoint>(sorted.size + 1)
    result.add(
        ChartPoint(
            at = product.startedAt,
            weight = product.initialWeight,
            label = "Старт",
            isInitial = true
        )
    )
    sorted.forEachIndexed { index, w ->
        result.add(
            ChartPoint(
                at = w.at,
                weight = w.weight,
                label = "Взв. #${index + 1}",
                isInitial = false
            )
        )
    }
    return result
}

fun calculateDryingStats(product: Product, nowMs: Long = System.currentTimeMillis()): DryingStats {
    val lastWeighingAt = product.weighings.maxOfOrNull { it.at }
    val endReference = product.archivedAt ?: max(nowMs, lastWeighingAt ?: product.startedAt)
    val elapsedDays = ((endReference - product.startedAt).coerceAtLeast(0L)).toDouble() / DAY_MILLIS.toDouble()

    val lossGrams = product.initialWeight - product.currentWeight
    val lossPct = product.lossPercent
    val remGrams = (product.currentWeight - product.targetWeight).coerceAtLeast(0.0)
    val remPct = (product.targetLoss - lossPct).coerceAtLeast(0.0)

    val measureSpanDays = if (lastWeighingAt != null && lastWeighingAt > product.startedAt) {
        (lastWeighingAt - product.startedAt).toDouble() / DAY_MILLIS.toDouble()
    } else {
        elapsedDays
    }

    val effectiveDays = max(measureSpanDays, 0.0)
    val avgGrams = if (product.weighings.isNotEmpty() && effectiveDays >= 0.05) {
        lossGrams / effectiveDays
    } else null
    val avgPct = if (product.weighings.isNotEmpty() && effectiveDays >= 0.05) {
        lossPct / effectiveDays
    } else null

    return DryingStats(
        elapsedDays = elapsedDays,
        absoluteLossGrams = lossGrams,
        lossPercent = lossPct,
        remainingGramsToTarget = remGrams,
        remainingPercentToTarget = remPct,
        avgGramsPerDay = avgGrams,
        avgPercentPerDay = avgPct
    )
}

@Composable
fun ProductWeightChart(product: Product) {
    val points = buildChartPoints(product)
    val stats = calculateDryingStats(product)
    val primaryColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val allWeights = points.map { it.weight } + product.targetWeight
    val rawMin = allWeights.minOrNull() ?: product.targetWeight
    val rawMax = allWeights.maxOrNull() ?: product.initialWeight
    val span = (rawMax - rawMin).coerceAtLeast(10.0)
    val yMin = (rawMin - span * 0.12).coerceAtLeast(0.0)
    val yMax = rawMax + span * 0.12

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Сводка динамики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Длительность: ${decimal(stats.elapsedDays)} сут.")
                Text("Фактическая убыль: ${grams(stats.absoluteLossGrams)} г (${decimal(stats.lossPercent)}%)")
                if (stats.remainingGramsToTarget > 0.0) {
                    Text("До цели осталось: ${grams(stats.remainingGramsToTarget)} г (${decimal(stats.remainingPercentToTarget)}%)")
                } else {
                    Text(
                        "Целевой вес (${grams(product.targetWeight)} г) достигнут",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (stats.avgGramsPerDay != null && stats.avgPercentPerDay != null) {
                    Text("Средний темп: ${decimal(stats.avgGramsPerDay)} г/сут (${decimal(stats.avgPercentPerDay)} %/сут)")
                } else {
                    Text(
                        "Добавьте взвешивание, чтобы рассчитать средний суточный темп усушки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("График веса (г)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Пунктир — цель ${grams(product.targetWeight)} г",
                        style = MaterialTheme.typography.bodySmall,
                        color = targetColor
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.height(200.dp).padding(end = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("${grams(yMax)} г", style = MaterialTheme.typography.labelSmall)
                        Text("${grams((yMax + yMin) / 2.0)} г", style = MaterialTheme.typography.labelSmall)
                        Text("${grams(yMin)} г", style = MaterialTheme.typography.labelSmall)
                    }

                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(200.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val padTop = 10.dp.toPx()
                        val padBottom = 10.dp.toPx()
                        val padLeft = 8.dp.toPx()
                        val padRight = 8.dp.toPx()
                        val plotW = (w - padLeft - padRight).coerceAtLeast(1f)
                        val plotH = (h - padTop - padBottom).coerceAtLeast(1f)

                        fun yForWeight(weightVal: Double): Float {
                            val ratio = ((weightVal - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
                            return padTop + plotH * (1f - ratio)
                        }

                        // Сетка по горизонтали
                        listOf(0f, 0.5f, 1f).forEach { r ->
                            val y = padTop + plotH * r
                            drawLine(
                                color = gridColor,
                                start = Offset(padLeft, y),
                                end = Offset(w - padRight, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Линия целевого веса
                        val targetY = yForWeight(product.targetWeight)
                        drawLine(
                            color = targetColor,
                            start = Offset(padLeft, targetY),
                            end = Offset(w - padRight, targetY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )

                        val minTime = points.first().at
                        val maxTime = points.last().at
                        val timeRange = (maxTime - minTime).coerceAtLeast(1L)

                        val offsets = points.mapIndexed { index, pt ->
                            val xRatio = if (points.size == 1) {
                                0.5f
                            } else if (maxTime > minTime) {
                                (pt.at - minTime).toFloat() / timeRange.toFloat()
                            } else {
                                index.toFloat() / (points.size - 1).toFloat()
                            }
                            Offset(
                                x = padLeft + plotW * xRatio.coerceIn(0f, 1f),
                                y = yForWeight(pt.weight)
                            )
                        }

                        if (offsets.size >= 2) {
                            val fillPath = Path().apply {
                                moveTo(offsets.first().x, padTop + plotH)
                                offsets.forEach { lineTo(it.x, it.y) }
                                lineTo(offsets.last().x, padTop + plotH)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.02f)),
                                    startY = padTop,
                                    endY = padTop + plotH
                                )
                            )

                            val linePath = Path().apply {
                                moveTo(offsets.first().x, offsets.first().y)
                                for (i in 1 until offsets.size) {
                                    lineTo(offsets[i].x, offsets[i].y)
                                }
                            }
                            drawPath(
                                path = linePath,
                                color = primaryColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        offsets.forEach { center ->
                            drawCircle(
                                color = primaryColor,
                                radius = 5.dp.toPx(),
                                center = center
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Старт: ${dateTime(points.first().at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (points.size > 1) {
                        Text(
                            "Крайняя: ${dateTime(points.last().at)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (points.size > 1) {
            Text("Контрольные точки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            points.forEachIndexed { index, pt ->
                val prevWeight = if (index > 0) points[index - 1].weight else pt.weight
                val deltaGrams = pt.weight - prevWeight
                val totalLossFromStart = (product.initialWeight - pt.weight) / product.initialWeight * 100.0
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${pt.label} · ${dateTime(pt.at)}", style = MaterialTheme.typography.bodyMedium)
                        if (!pt.isInitial) {
                            val sign = if (deltaGrams > 0) "+" else ""
                            Text(
                                "К пред.: $sign${grams(deltaGrams)} г · Всего убыль: ${decimal(totalLossFromStart)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("${grams(pt.weight)} г", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
            }
        }
    }
}