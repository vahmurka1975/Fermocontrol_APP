package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.ui.components.BatchCard
import ru.fermocontrol.fermocontrol.ui.theme.Amber
import ru.fermocontrol.fermocontrol.ui.theme.Bg
import ru.fermocontrol.fermocontrol.ui.theme.Card
import ru.fermocontrol.fermocontrol.ui.theme.Emerald
import ru.fermocontrol.fermocontrol.ui.theme.EmeraldLine
import ru.fermocontrol.fermocontrol.ui.theme.EmeraldSoft
import ru.fermocontrol.fermocontrol.ui.theme.Ink
import ru.fermocontrol.fermocontrol.ui.theme.Line
import ru.fermocontrol.fermocontrol.ui.theme.Muted

enum class MainTab(val label: String) {
    Products("Продукты"),
    Weigh("Взвесить"),
    Archive("Архив"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    batches: List<Batch>,
    onAdd: () -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
    onBatchClick: (Batch) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(MainTab.Products) }

    val filtered = remember(batches, query, tab) {
        batches.filter { batch ->
            val matchesTab = when (tab) {
                MainTab.Archive -> batch.archived
                else -> !batch.archived
            }
            val matchesQuery = query.isBlank() ||
                batch.name.contains(query, ignoreCase = true) ||
                batch.number.toString().contains(query)
            matchesTab && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
    ) {
        // Шапка
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Фермоконтроль",
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "журнал партий",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }

            HeaderIconButton(emoji = "⚠", color = Amber, onClick = onAlerts)
            Spacer(Modifier.width(10.dp))
            HeaderIconButton(emoji = "+", color = Emerald, onClick = onAdd)
            Spacer(Modifier.width(10.dp))
            HeaderIconButton(emoji = "⚙", color = Muted, onClick = onSettings)
        }

        // Поиск
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Поиск по названию или номеру…", color = Muted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldLine,
                unfocusedBorderColor = Line,
                cursorColor = Emerald,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
            ),
        )

        Spacer(Modifier.height(14.dp))

        // Вкладки-пилюли
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MainTab.entries.forEach { t ->
                TabPill(
                    label = t.label,
                    selected = tab == t,
                    onClick = { tab = t },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Список
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (tab == MainTab.Weigh) "Нет активных партий для взвешивания"
                    else "Партии не найдены",
                    color = Muted,
                    fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { batch ->
                    BatchCard(batch = batch, onClick = { onBatchClick(batch) })
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    emoji: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Card,
        border = BorderStroke(1.dp, Line),
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 20.sp, color = color)
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) EmeraldSoft else Color.Transparent
    val fg = if (selected) Emerald else Muted
    val border = if (selected) EmeraldLine else Line

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}