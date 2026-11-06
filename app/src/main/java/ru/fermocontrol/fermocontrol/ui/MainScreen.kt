package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.ui.theme.*

private val MutedLine = Line          // вместо отсутствующего CardLine
private val EmeraldDeep = Color(0xFF1A1F18)

@Composable
fun MainScreen(
    batches: List<Batch>,
    onSelect: (Batch) -> Unit = {},
    onWeigh: (Batch) -> Unit = {},
    onAdd: () -> Unit = {},
    onSettings: () -> Unit = {},
    onProfile: () -> Unit = {},
) {
    var sort by remember { mutableStateOf("number") }
    var query by remember { mutableStateOf("") }
    var onlyActive by remember { mutableStateOf(false) }

    val filtered = remember(batches, sort, query, onlyActive) {
        batches
            .filter { if (onlyActive) !it.archived else true }
            .filter { query.isBlank() || it.name.contains(query, true) }
            .let { list ->
                when (sort) {
                    "attention" -> list.sortedBy { !it.requiresAction() }
                    "new" -> list.sortedByDescending { it.createdAtEpochMs }
                    else -> list.sortedBy { it.number }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // ── Шапка ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Камера",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
                Text(
                    text = "Партии созревания",
                    fontSize = 13.sp,
                    color = Muted
                )
            }

            HeaderTool(
                icon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
                onClick = { query = if (query.isEmpty()) " " else "" }
            )
            HeaderTool(
                icon = { Icon(Icons.Outlined.Notifications, null, tint = Ink) },
                hasAttention = batches.any { it.requiresAction() },
                count = batches.count { it.requiresAction() },
                onClick = { onlyActive = !onlyActive }
            )
            HeaderTool(
                icon = { Icon(Icons.Outlined.Settings, null, tint = Ink) },
                accent = true,
                onClick = onSettings
            )
        }

        // ── Поиск ──
        if (query.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = Muted)
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink),
                    singleLine = true,
                    placeholder = { Text("Название партии…", color = Muted) }
                )
            }
        }

        // ── Сортировка ──
        SortRow(sort = sort, onSelect = { sort = it })

        // ── Список ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { batch ->
                BatchCard(
                    batch = batch,
                    onClick = { onSelect(batch) },
                    onWeigh = { onWeigh(batch) }
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    // ── Кнопка добавления ──
    Box(modifier = Modifier.fillMaxSize()) {
        RoundedButton(
            text = "Новая закладка",
            accent = true,
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .fillMaxWidth(0.9f)
        )
    }
}

// ───────────────────────── Производные данные партии ─────────────────────────

private fun Batch.currentWeightGrams(): Double =
    logs.lastOrNull()?.weightGrams ?: startWeightGrams

private fun Batch.lossPercent(): Double {
    val cur = currentWeightGrams()
    if (startWeightGrams <= 0.0) return 0.0
    return ((startWeightGrams - cur) / startWeightGrams) * 100.0
}

private fun Batch.targetProgress(): Float {
    if (targetLossPercent <= 0.0) return 0f
    return (lossPercent() / targetLossPercent).toFloat().coerceIn(0f, 1f)
}

private fun Batch.day(): Int {
    if (createdAtEpochMs <= 0L) return 0
    val now = System.currentTimeMillis()
    return ((now - createdAtEpochMs) / (1000L * 60 * 60 * 24)).toInt()
}

private fun Batch.requiresAction(): Boolean =
    lossPercent() >= targetLossPercent || logs.lastOrNull()?.needsCheck == true

private fun Batch.nextActionText(): String = when {
    logs.lastOrNull()?.needsCheck == true -> "Требует проверки"
    lossPercent() >= targetLossPercent -> "Цель достигнута"
    archived -> "В архиве"
    else -> "Контрольное взвешивание"
}

// ─────────────────────────────── Компоненты ───────────────────────────────

@Composable
private fun HeaderTool(
    icon: @Composable () -> Unit,
    accent: Boolean = false,
    hasAttention: Boolean = false,
    count: Int = 0,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (accent) Emerald else Card)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
        if (hasAttention) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Amber),
                contentAlignment = Alignment.Center
            ) {
                if (count > 0) {
                    Text(
                        text = count.toString().take(2),
                        fontSize = 8.sp,
                        color = Bg,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SortRow(sort: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "number" to "По номеру",
        "attention" to "Сначала внимание",
        "new" to "Сначала новые"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (key, label) ->
            val selected = sort == key
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (selected) Emerald else Muted,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun RoundedButton(
    text: String,
    accent: Boolean,
    expanded: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .let { if (expanded) it.fillMaxWidth() else it }
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) Emerald else Card)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (accent) EmeraldDeep else Ink
        )
    }
}

@Composable
private fun BatchCard(
    batch: Batch,
    onClick: () -> Unit,
    onWeigh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        // Верхняя строка: номер + название + потеря %
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp, 42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Line),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = batch.number.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Muted
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = batch.name,
                    fontSize = 16.sp,
                    color = Ink
                )
                Text(
                    text = "${batch.rawMaterial} · день ${batch.day()}",
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f%%".format(batch.lossPercent()),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald
                )
                Text(
                    text = "потеря",
                    fontSize = 10.sp,
                    color = Muted
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Крупный вес
        Text(
            text = "%.0f г".format(batch.currentWeightGrams()),
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = Ink
        )

        Spacer(Modifier.height(8.dp))

        // Прогресс-бар к цели
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Line)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(batch.targetProgress())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (batch.requiresAction()) Amber else Emerald)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Нижняя строка: действие + кнопка Взвесить
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (batch.requiresAction()) Amber else Emerald)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = batch.nextActionText(),
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (batch.requiresAction()) Amber else Line)
                    .clickable { onWeigh() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Взвесить",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (batch.requiresAction()) Bg else Ink
                )
            }
        }
    }
}