package ru.fermocontrol.fermocontrol.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.computeMetrics
import ru.fermocontrol.fermocontrol.model.toPercentString
import ru.fermocontrol.fermocontrol.ui.theme.*

@Composable
fun BatchCard(
    batch: Batch,
    onClick: () -> Unit,
) {
    val metrics = computeMetrics(batch)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Card,
        border = BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Шапка карточки
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${batch.number} · ${batch.name}",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (batch.category.isNotBlank()) {
                        Text(
                            text = batch.category,
                            color = Muted,
                            fontSize = 13.sp,
                        )
                    }
                }
                // Процент усушки
                Text(
                    text = "${metrics.lostPercent.toPercentString(1)}%",
                    color = Emerald,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Прогресс к цели
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Цель ${metrics.targetLossPercent.toPercentString(1)}%",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "${(metrics.progress * 100).toInt()}%",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { metrics.progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50)),
                    color = Emerald,
                    trackColor = EmeraldSoft,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Нижняя строка: вес, дни, темп
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricLabel(
                    label = "вес",
                    value = "${metrics.currentWeightGrams.toInt()} г",
                )
                MetricLabel(
                    label = "в камере",
                    value = "${metrics.daysElapsed} дн",
                )
                MetricLabel(
                    label = "темп",
                    value = metrics.pacePercentPerDay
                        ?.let { "${it.toPercentString(2)}%/сут" }
                        ?: "—",
                )
            }

            // Предупреждение "требует проверки"
            if (batch.logs.lastOrNull()?.needsCheck == true) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "⚠ Требует проверки",
                    color = Amber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MetricLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Muted, fontSize = 11.sp)
        Text(text = value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}