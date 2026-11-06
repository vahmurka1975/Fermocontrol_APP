package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.Metrics
import ru.fermocontrol.fermocontrol.model.computeMetrics
import ru.fermocontrol.fermocontrol.model.requiresAttention
import ru.fermocontrol.fermocontrol.ui.components.BatchCard
import ru.fermocontrol.fermocontrol.ui.theme.*

private enum class MainTab(val label: String) {
    PRODUCTS("Продукты"),
    WEIGH("Взвесить"),
    ARCHIVE("Архив"),
}

private enum class BatchSort(val label: String) {
    NUMBER("По номеру"),
    ATTENTION("Сначала внимание"),
    NEW("Сначала новые"),
}

private data class BatchRow(
    val batch: Batch,
    val metrics: Metrics,
    val attention: Boolean,
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(
    batches: List<Batch>,
    onSelect: (Batch) -> Unit = {},
    onWeigh: (Batch) -> Unit = {},
    onAdd: () -> Unit = {},
    onSettings: () -> Unit = {},
    onProfile: () -> Unit = {},
    onWeighBatch: (Batch) -> Unit = onWeigh,
) {
    var tab by rememberSaveable {
        mutableStateOf(MainTab.PRODUCTS)
    }
    var sort by rememberSaveable {
        mutableStateOf(BatchSort.NUMBER)
    }
    var query by rememberSaveable {
        mutableStateOf("")
    }
    var category by rememberSaveable {
        mutableStateOf("")
    }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Обновляем дату и возраст партий, если экран долго открыт.
    val nowEpochMs by produceState(
        initialValue = System.currentTimeMillis()
    ) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }

    val todayLabel = remember(nowEpochMs) {
        SimpleDateFormat(
            "d MMMM yyyy",
            Locale.forLanguageTag("ru-RU"),
        ).format(Date(nowEpochMs))
    }

    // Один набор метрик используется и в списке, и в карточках.
    val rows = remember(batches, nowEpochMs) {
        batches.map { batch ->
            val metrics = computeMetrics(batch, nowEpochMs)

            BatchRow(
                batch = batch,
                metrics = metrics,
                attention = batch.requiresAttention(metrics),
            )
        }
    }

    val counts = mapOf(
        MainTab.PRODUCTS to rows.count { !it.batch.archived },
        MainTab.WEIGH to rows.count { it.attention },
        MainTab.ARCHIVE to rows.count { it.batch.archived },
    )

    val categories = remember(batches) {
        batches
            .map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val categoryOptions = remember(categories) {
        listOf("" to "Все категории") +
            categories.map { it to it }
    }

    // Например, после загрузки другого журнала.
    LaunchedEffect(categories) {
        if (category.isNotEmpty() && category !in categories) {
            category = ""
        }
    }

    val filtered = remember(rows, tab, query, category, sort) {
        val q = query.trim()

        val matching = rows.filter { row ->
            val batch = row.batch

            val matchesTab = when (tab) {
                MainTab.PRODUCTS -> !batch.archived
                MainTab.WEIGH -> row.attention
                MainTab.ARCHIVE -> batch.archived
            }

            val matchesCategory =
                category.isEmpty() ||
                    batch.category.trim() == category

            val matchesQuery =
                q.isEmpty() ||
                    batch.name.contains(q, ignoreCase = true) ||
                    batch.rawMaterial.contains(q, ignoreCase = true) ||
                    batch.category.contains(q, ignoreCase = true) ||
                    batch.number.toString()
                        .padStart(2, '0')
                        .contains(q)

            matchesTab && matchesCategory && matchesQuery
        }

        when (sort) {
            BatchSort.NUMBER ->
                matching.sortedBy { it.batch.number }

            BatchSort.ATTENTION ->
                matching.sortedWith(
                    compareBy<BatchRow> { !it.attention }
                        .thenBy { it.batch.number }
                )

            BatchSort.NEW ->
                matching.sortedByDescending {
                    it.batch.createdAtEpochMs
                }
        }
    }

    LaunchedEffect(tab, query, category, sort) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .imePadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "controls") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "ФермоКонтроль",
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            text = "ЖУРНАЛ СОЗРЕВАНИЯ",
                            color = Muted,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    HeaderAction(
                        icon = Icons.Outlined.Notifications,
                        description = "Внимание",
                        count = counts.getValue(MainTab.WEIGH),
                        onClick = {
                            focusManager.clearFocus()

                            // Не оставляем скрывающие результаты фильтры.
                            query = ""
                            category = ""
                            tab = MainTab.WEIGH
                        },
                    )

                    HeaderAction(
                        icon = Icons.Outlined.Add,
                        description = "Новая закладка",
                        accent = true,
                        onClick = onAdd,
                    )

                    HeaderAction(
                        icon = Icons.Outlined.Settings,
                        description = "Настройки",
                        onClick = onSettings,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "Камера",
                        modifier = Modifier.weight(1f),
                        color = Ink,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = todayLabel,
                        modifier = Modifier.padding(
                            start = 8.dp,
                            bottom = 4.dp,
                        ),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }

                // Поиск отображается всегда, включая пустой запрос.
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            text = "Название, номер или сырьё",
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Очистить поиск",
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                        },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedContainerColor = Card,
                        unfocusedContainerColor = Card,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLeadingIconColor = Muted,
                        unfocusedLeadingIconColor = Muted,
                        focusedTrailingIconColor = Muted,
                        unfocusedTrailingIconColor = Muted,
                        focusedPlaceholderColor = Muted,
                        unfocusedPlaceholderColor = Muted,
                        cursorColor = Emerald,
                    ),
                )

                MainTabs(
                    selected = tab,
                    counts = counts,
                    onSelect = {
                        focusManager.clearFocus()
                        tab = it
                    },
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceMenu(
                        current = category,
                        options = categoryOptions,
                        onSelect = {
                            focusManager.clearFocus()
                            category = it
                        },
                        modifier = Modifier.weight(1f),
                    )

                    ChoiceMenu(
                        current = sort,
                        options = BatchSort.entries.map {
                            it to it.label
                        },
                        onSelect = {
                            focusManager.clearFocus()
                            sort = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Показано: ${filtered.size}",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyBatchState(
                    tab = tab,
                    hasFilters =
                        query.isNotBlank() || category.isNotEmpty(),
                    onClearFilters = {
                        query = ""
                        category = ""
                    },
                    onAdd = onAdd,
                )
            }
        } else {
            items(
                items = filtered,
                key = { "batch-${it.batch.id}" },
            ) { row ->
                BatchCard(
    batch = row.batch,
    onClick = {
        focusManager.clearFocus()
        onSelect(row.batch)
    },
    onWeigh = {
        focusManager.clearFocus()
        onWeighBatch(row.batch)
    },
)
            }
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    count: Int = 0,
    accent: Boolean = false,
) {
    Box(
        modifier = Modifier.size(48.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (accent) Emerald else Card,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (count > 0) {
                    "$description: $count"
                } else {
                    description
                },
                tint = when {
                    accent -> Bg
                    count > 0 -> Amber
                    else -> Ink
                },
                modifier = Modifier.size(21.dp),
            )
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(Amber, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = Bg,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MainTabs(
    selected: MainTab,
    counts: Map<MainTab, Int>,
    onSelect: (MainTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .selectableGroup()
            .padding(4.dp),
    ) {
        MainTab.entries.forEach { tab ->
            val active = selected == tab

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) EmeraldSoft else Color.Transparent
                    )
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${tab.label} ${counts[tab] ?: 0}",
                    color = if (active) Emerald else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (active) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceMenu(
    current: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    val label = options
        .firstOrNull { it.first == current }
        ?.second
        .orEmpty()

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = Card,
                contentColor = Muted,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = title,
                            color = if (value == current) Emerald else Ink,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyBatchState(
    tab: MainTab,
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    onAdd: () -> Unit,
) {
    val title = when {
        hasFilters -> "Ничего не найдено"
        tab == MainTab.WEIGH -> "Нет партий, требующих внимания"
        tab == MainTab.ARCHIVE -> "Архив пуст"
        else -> "Пока нет продуктов"
    }

    val subtitle = when {
        hasFilters ->
            "Измени поисковый запрос или выбранную категорию."

        tab == MainTab.WEIGH ->
            "Здесь появятся партии с отметкой проверки " +
                "или достигнутой целью усушки."

        tab == MainTab.ARCHIVE ->
            "Завершённые партии появятся здесь."

        else ->
            "Добавь первую закладку, чтобы начать журнал."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = subtitle,
            color = Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        if (hasFilters) {
            TextButton(onClick = onClearFilters) {
                Text("Сбросить поиск и категорию")
            }
        } else if (tab == MainTab.PRODUCTS) {
            Button(onClick = onAdd) {
                Text("Новая закладка")
            }
        }
    }
}