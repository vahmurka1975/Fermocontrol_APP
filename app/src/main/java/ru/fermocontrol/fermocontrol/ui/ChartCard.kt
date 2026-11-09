@file:OptIn(ExperimentalLayoutApi::class)

package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

enum class LegendKind { Fact, Forecast, Target, Today }

data class ChartMilestone(
    val label: String,
    val value: String,
    val hint: String,
    val forecast: Boolean = false,
)

data class ChartLegendItem(val kind: LegendKind, val text: String)

private val CGreen = Color(0xFFB5D9AB)
private val CAmber = Color(0xFFEDBC77)
private val CMuted = Color(0xFFA1ADA2)

@Composable
fun ChartCard(
    title: String,
    eta: String,
    etaWarning: Boolean,
    targetLabel: String,
    targetValue: String,
    unitsStart: String,
    unitsEnd: String,
    legend: List<ChartLegendItem>,
    milestones: List<ChartMilestone>,
    footnote: String,
    chart: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .padding(top = 20.dp, bottom = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF1C281F))
            .border(1.dp, Color(0xFF3C5034), shape)
            .padding(14.dp),
    ) {
        // .chart-header: на ширине <= 400 элементы идут друг под другом, иначе в строку.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            val etaColor = if (etaWarning) CAmber else CGreen
            if (maxWidth <= 400.dp) {
                Column {
                    Text(title.uppercase(), color = CMuted, fontSize = 10.sp,
                        letterSpacing = 0.3.sp, fontWeight = FontWeight.ExtraBold)
                    Text(eta, color = etaColor, fontSize = 11.sp, lineHeight = 17.6.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title.uppercase(), color = CMuted, fontSize = 10.sp,
                        letterSpacing = 0.3.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f))
                    Text(eta, color = etaColor, fontSize = 11.sp, lineHeight = 17.6.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }
        }

        // .chart-target-strip
        val stripShape = RoundedCornerShape(10.dp)
        FlowRow(
            Modifier.padding(top = 4.dp, bottom = 12.dp).fillMaxWidth()
                .border(1.dp, Color(0x40B5D9AB), stripShape)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(targetLabel, color = Color(0xFFB4C5A9), fontSize = 11.sp)
            Text(targetValue, color = CGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }

        // .chart-units
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(unitsStart, color = CMuted, fontSize = 10.sp)
            Text(unitsEnd, color = CMuted, fontSize = 10.sp)
        }

        chart()

        // .chart-legend: margin-top 13, gap 8 / 14
        FlowRow(
            Modifier.padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            legend.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendLine(item.kind)
                    Text(item.text, color = CMuted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }

        // .chart-milestones: 2 колонки, на ширине <= 400 одна
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val cols = if (maxWidth <= 400.dp) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                milestones.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { Milestone(it, Modifier.weight(1f)) }
                        if (row.size < cols) Spacer(Modifier.weight((cols - row.size).toFloat()))
                    }
                }
            }
        }

        Text(footnote, color = Color(0xFF91A287), fontSize = 10.sp,
            lineHeight = 16.sp, modifier = Modifier.padding(top = 11.dp))
    }
}

@Composable
private fun Milestone(m: ChartMilestone, modifier: Modifier) {
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier.clip(shape).background(Color(0xFF17221A))
            .border(1.dp, Color(0xFF303E34), shape).padding(11.dp),
    ) {
        Text(m.label, color = CMuted, fontSize = 10.sp)
        Text(m.value, color = if (m.forecast) CAmber else Color(0xFFE0EAD8),
            fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp))
        Text(m.hint, color = Color(0xFF9AAA90), fontSize = 10.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LegendLine(kind: LegendKind) {
    val color = when (kind) {
        LegendKind.Fact -> CGreen
        LegendKind.Forecast -> CAmber
        LegendKind.Target -> CGreen.copy(alpha = 0.7f)
        LegendKind.Today -> CMuted
    }
    Canvas(Modifier.size(width = 16.dp, height = 2.5.dp)) {
        val dash = when (kind) {
            LegendKind.Fact -> null
            LegendKind.Forecast -> floatArrayOf(4.dp.toPx(), 4.dp.toPx())
            else -> floatArrayOf(2.dp.toPx(), 3.dp.toPx())
        }
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = dash?.let { PathEffect.dashPathEffect(it) })
    }
}

/** .recommendation--drying */
@Composable
fun DryingRecommendation(
    head: String,
    facts: List<Pair<String, String>>,   // подпись to значение (значение выделяется)
    steps: List<Pair<String, String>>,   // жирное начало to остальной текст
    stale: String?,                      // блок .drying-stale, если замер устарел
    note: String,
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape)
            .background(Color(0xFF2A291E)).border(1.dp, Color(0xFF6A5734), shape)
            .padding(16.dp),
    ) {
        Text(head, color = Color(0xFFEFC78E), fontSize = 14.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.Medium)
        FlowRow(Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            facts.forEach { (l, v) ->
                Row {
                    Text("$l ", color = Color(0xFFC9BB9F), fontSize = 11.sp)
                    Text(v, color = Color(0xFFF1D0A1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            steps.forEachIndexed { i, (bold, rest) ->
                Row {
                    Text("${i + 1}. ", color = Color(0xFFC9BB9F), fontSize = 12.sp, lineHeight = 19.8.sp)
                    Text(
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
        append(" $rest")
    },
    color = Color(0xFFC9BB9F), fontSize = 12.sp, lineHeight = 19.8.sp,
)
                }
            }
        }
        stale?.let {
            Row(Modifier.padding(top = 12.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(CAmber))
                Text(it, color = Color(0xFFC9BB9F), fontSize = 12.sp, lineHeight = 19.8.sp,
                    modifier = Modifier.padding(start = 10.dp))
            }
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(Color(0xFF574A32)))
        Text(note, color = Color(0xFFA99A7E), fontSize = 10.sp, lineHeight = 15.5.sp,
            modifier = Modifier.padding(top = 10.dp))
    }
}