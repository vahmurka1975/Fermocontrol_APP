package ru.fermocontrol.fermocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.computeMetrics
import ru.fermocontrol.fermocontrol.model.computeStatus
import ru.fermocontrol.fermocontrol.model.nextActionText
import ru.fermocontrol.fermocontrol.ui.fmtDateShort
import ru.fermocontrol.fermocontrol.ui.fmtGrams
import ru.fermocontrol.fermocontrol.ui.fmtPercentChange
import ru.fermocontrol.fermocontrol.ui.theme.*

/** Карточка продукта: структура .product-card макета v0.4.1. */
@Composable
fun BatchCard(
    batch: Batch,
    onClick: () -> Unit,
    onWeigh: () -> Unit,
    nowEpochMs: Long = System.currentTimeMillis(),
) {
    val metrics = computeMetrics(batch, nowEpochMs)
    val status = computeStatus(batch, nowEpochMs)
    val warning = status.needsAttention
    val shape = RoundedCornerShape(20.dp)
    val lastLog = batch.logs.maxByOrNull { it.atEpochMs }
    val start = batch.startWeightGrams
    val rawLoss = if (start > 0.0) (start - metrics.currentWeightGrams) / start * 100.0 else 0.0

    val dayLabel = if (metrics.daysElapsed == 0) "Закладка сегодня" else "День ${metrics.daysElapsed}"
    val subtitle = listOf(batch.rawMaterial.ifBlank { batch.category }, dayLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val caption = if (lastLog != null) "замер " + fmtDateShort(lastLog.atEpochMs)
    else "начальный вес · без замеров"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(FcCardTop, FcCardBottom)))
            .border(1.dp, if (warning) FcCardWarnLine else FcCardLine, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FcNumberBg)
                        .border(1.dp, FcNumberLine, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = batch.number.toString().padStart(2, '0'),
                        color = FcNumberInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = batch.name,
                        color = Ink,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        color = Muted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Icon(
    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
    contentDescription = null
)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 19.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = fmtGrams(metrics.currentWeightGrams),
                            color = Ink,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.9).sp,
                        )
                        Text(
                            text = "г",
                            color = FcWeightUnit,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
                        )
                    }
                    Text(
                        text = caption,
                        color = FcCaption,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmtPercentChange(rawLoss),
                        color = FcLoss,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "от начального веса",
                        color = FcCaption,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FcTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(metrics.progress.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (warning) FcFillWarn else FcFill),
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(FcFooterLine))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 59.dp)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (warning) Amber else FcDot),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = nextActionText(batch, status),
                    color = if (warning) FcNextActionWarn else FcNextAction,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }

            val buttonShape = RoundedCornerShape(10.dp)
            if (batch.archived) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(buttonShape)
                        .border(1.dp, Line, buttonShape)
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Журнал", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                val due = status.isDueWeigh
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(buttonShape)
                        .background(if (due) FcDueBg else FcWeighBg)
                        .border(1.dp, if (due) FcDueBg else FcWeighLine, buttonShape)
                        .clickable(role = Role.Button, onClick = onWeigh)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Взвесить",
                        color = if (due) FcDueInk else FcWeighInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
