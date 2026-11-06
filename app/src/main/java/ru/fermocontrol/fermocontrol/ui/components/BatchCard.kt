package ru.fermocontrol.fermocontrol.ui.components

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
import ru.fermocontrol.fermocontrol.model.*
import ru.fermocontrol.fermocontrol.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Карточка партии — главный элемент списка «Продукты» (Design 04).
 */
@Composable
fun BatchCard(
    batch: Batch,
    onClick: () -> Unit,
    onWeigh: () -> Unit,
) {
    val m = computeMetrics(batch)
    val archived = batch.archived

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (archived) BgElev else Card)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // ── Верхняя строка: номер, название, сырьё · день ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Номер — квадрат (40×42), не кружок
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (archived) CardHover else EmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = batch.number.toString().padStart(2, '0'),
                    color = if (archived) Muted else Emerald,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = batch.name,
                    color = if (archived) Muted else Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = buildString {
                        append(batch.rawMaterial.ifBlank { batch.category })
                        if (m.daysElapsed >= 0) append(" · День ${m.daysElapsed + 1}")
                    },
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(8.dp))

            // Потеря, % — текстом справа (как в Design 04)
            Text(
                text = percentSigned(m.lostPercent),
                color = percentColor(m),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Текущий вес — крупно, с единицей «г» ──
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatWeight(m.currentWeightGrams),
                color = if (archived) Muted else Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "г",
                color = Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = "текущий вес",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        // ── Прогресс к цели усушки (тонкая полоса 4dp) ──
        Text(
            text = if (archived) "в архиве" else "к цели усушки ${m.targetLossPercent.toPercentString()}% — ${(m.progress * 100).roundToInt()}%",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        ProgressBarThin(progress = m.progress, warn = m.toTargetPercent <= 0.5 || needsAttention(m))

        Spacer(Modifier.height(14.dp))

        // ── Футер: статус + кнопка ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusDot(warn = needsAttention(m))
            Spacer(Modifier.width(8.dp))
            Text(
                text = nextActionLabel(m),
                color = if (needsAttention(m)) Amber else Muted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )

            if (!archived) {
                Button(
                    onClick = onWeigh,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (needsAttention(m)) Amber else AmberSoft,
                        contentColor = if (needsAttention(m)) Bg else Amber
                    ),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Взвесить", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(
                    text = "Журнал",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Вспомогательные элементы карточки
// ─────────────────────────────────────────────────────────────

@Composable
private fun ProgressBarThin(progress: Double, warn: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(if (warn) Amber else Emerald)
        )
    }
}

@Composable
private fun StatusDot(warn: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(when {
                warn -> Amber
                else -> Muted
            })
    )
}

// ─────────────────────────────────────────────────────────────
//  Форматирование и логика текстов
// ─────────────────────────────────────────────────────────────

private fun formatWeight(grams: Double): String {
    val r = Math.round(grams)
    return "%,d".format(r).replace(',', ' ')
}

private fun percentSigned(value: Double): String {
    val sign = if (value < 0) "+" else "−"
    return "$sign${Math.abs(value).toPercentString()}%"
}

@Composable
private fun percentColor(m: Metrics): Color {
    return when {
        m.toTargetPercent <= 0.0 -> Emerald       // цель достигнута/перевыполнена
        m.pacePercentPerDay != null && m.pacePercentPerDay > 0 && m.pacePercentPerDay >= 3.0 -> Amber
        else -> Muted
    }
}

private fun needsAttention(m: Metrics): Boolean {
    // «пора взвесить»: до цели близко, либо темп упал/пересох
    return m.toTargetPercent <= 0.5 ||
        (m.pacePercentPerDay != null && m.pacePercentPerDay <= 0.0) ||
        (m.estimatedDaysLeft != null && m.estimatedDaysLeft <= 1)
}

private fun nextActionLabel(m: Metrics): String {
    return when {
        m.daysElapsed == 0 -> "Закладка сегодня"
        m.toTargetPercent <= 0.0 -> "Цель достигнута"
        needsAttention(m) -> "Пора взвесить"
        m.estimatedDaysLeft != null && m.estimatedDaysLeft > 0 -> "Взвешивание через ${m.estimatedDaysLeft} дн"
        else -> "Следите за усушкой"
    }
}