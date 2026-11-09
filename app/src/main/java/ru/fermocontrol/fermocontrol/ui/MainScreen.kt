package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.BatchStatus
import ru.fermocontrol.fermocontrol.model.computeStatus
import ru.fermocontrol.fermocontrol.ui.components.BatchCard
import ru.fermocontrol.fermocontrol.ui.components.FcPrimaryButton
import ru.fermocontrol.fermocontrol.ui.theme.Amber
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.fermocontrol.ui.theme.WarnBgSoft
import ru.fermocontrol.fermocontrol.ui.theme.WarnInk
import ru.fermocontrol.fermocontrol.ui.theme.WarnLine
import ru.fermocontrol.fermocontrol.ui.theme.WarnTint

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

/** Главный экран: структура .top + .content макета v0.4.1. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(
    batches: List<Batch>,
    onSelect: (Batch) -> Unit = {},
    onWeigh: (Batch) -> Unit = {},
    onAdd: () -> Unit = {},
    onSettings: () -> Unit = {},
    onProfile: () -> Unit = {},
) {
    val p = LocalAppPalette.current
    var tab by rememberSaveable { mutableStateOf(MainTab.PRODUCTS) }
    var sort by rememberSaveable { mutableStateOf(BatchSort.NUMBER) }
    var query by rememberSaveable { mutableStateOf("") }
    var attentionOnly by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }

    val rows = remember(batches, now) { batches.map { it to computeStatus(it, now) } }
    val activeCount = rows.count { !it.first.archived }
    val dueCount = rows.count { it.second.isDueWeigh }
    val archiveCount = rows.count { it.first.archived }
    val attentionCount = rows.count { it.second.needsAttention }
    val q = query.trim()

    val filtered = remember(rows, tab, attentionOnly, q, sort) {
        val matching = rows.filter { (b, s) ->
            val inTab = when {
                attentionOnly -> s.needsAttention
                tab == MainTab.PRODUCTS -> !b.archived
                tab == MainTab.WEIGH -> s.isDueWeigh
                else -> b.archived
            }
            inTab && (
                q.isEmpty() ||
                    b.name.contains(q, ignoreCase = true) ||
                    b.rawMaterial.contains(q, ignoreCase = true) ||
                    b.category.contains(q, ignoreCase = true) ||
                    b.number.toString().padStart(2, '0').contains(q)
                )
        }
        when (sort) {
            BatchSort.NUMBER -> matching.sortedBy { it.first.number }
            BatchSort.ATTENTION -> matching.sortedWith(
                compareBy<Pair<Batch, BatchStatus>> { !it.second.needsAttention }
                    .thenBy { it.first.number }
            )
            BatchSort.NEW -> matching.sortedByDescending { it.first.createdAtEpochMs }
        }
    }

    LaunchedEffect(tab, q, sort, attentionOnly) { listState.scrollToItem(0) }

    val listTitle = when {
        attentionOnly -> "Требуют внимания"
        tab == MainTab.PRODUCTS -> "Активные продукты"
        tab == MainTab.WEIGH -> "Пора взвесить"
        else -> "Архив"
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(p.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val narrow = maxWidth < 390.dp
        val hPad = if (maxWidth < 380.dp) 16.dp else 24.dp

        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(p.sheet, p.background)))
                    .padding(start = hPad, end = hPad, top = 20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (!narrow) {
                            val logoShape = RoundedCornerShape(13.dp, 13.dp, 16.dp, 16.dp)
                            Box(
                                modifier = Modifier
                                    .size(width = 37.dp, height = 41.dp)
                                    .clip(logoShape)
                                    .background(p.selected)
                                    .border(1.dp, p.chipLine, logoShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 16.dp, height = 20.dp)
                                        .border(1.5.dp, p.accent, RoundedCornerShape(4.dp))
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                        }
                        Column {
                            Text(
                                "ФермоКонтроль",
                                color = p.ink,
                                fontSize = if (narrow) 16.sp else 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.6).sp,
                                maxLines = 1,
                            )
                            Text(
                                "ЖУРНАЛ СОЗРЕВАНИЯ",
                                color = p.muted,
                                fontSize = 9.sp,
                                letterSpacing = 1.9.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    val hasAttention = attentionCount > 0
                    CircleAction(
                        icon = Icons.Outlined.Warning,
                        description = if (hasAttention) "Требуют внимания: $attentionCount" else "Продуктов, требующих внимания, нет",
                        tint = when {
                            attentionOnly -> p.background
                            hasAttention -> WarnTint
                            else -> p.body
                        },
                        bg = when {
                            attentionOnly -> WarnTint
                            hasAttention -> WarnBgSoft
                            else -> p.accent.copy(alpha = 0.03f)
                        },
                        border = when {
                            attentionOnly -> WarnTint
                            hasAttention -> WarnLine
                            else -> p.chipLine
                        },
                        badge = if (attentionCount > 99) "99+" else attentionCount.toString(),
                        badgeBg = when {
                            attentionOnly -> p.chip
                            hasAttention -> WarnTint
                            else -> p.chip
                        },
                        badgeInk = when {
                            attentionOnly -> WarnInk
                            hasAttention -> p.background
                            else -> p.strong
                        },
                        onClick = { focus.clearFocus(); attentionOnly = !attentionOnly },
                    )
                    CircleAction(
                        icon = Icons.Outlined.Add,
                        description = "Добавить продукт",
                        tint = p.accent,
                        bg = p.accent.copy(alpha = 0.08f),
                        border = p.selectedLine,
                        onClick = onAdd,
                    )
                    CircleAction(
                        icon = Icons.Outlined.Settings,
                        description = "Настройки и резервные копии",
                        tint = p.action,
                        bg = p.accent.copy(alpha = 0.015f),
                        border = p.chipLine,
                        onClick = onSettings,
                    )
                }

                Spacer(Modifier.height(18.dp))
                SearchField(query, { query = it }, { focus.clearFocus() })
                Spacer(Modifier.height(14.dp))

                val tabShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(tabShape)
                        .background(p.field)
                        .border(1.dp, p.cardLine, tabShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MainTab.entries.forEach { t ->
                        val selected = tab == t
                        val count = when (t) {
                            MainTab.PRODUCTS -> activeCount
                            MainTab.WEIGH -> dueCount
                            MainTab.ARCHIVE -> archiveCount
                        }
                        val s = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(s)
                                .background(if (selected) p.selected else Color.Transparent)
                                .clickable(role = Role.Tab) {
                                    focus.clearFocus()
                                    attentionOnly = false
                                    tab = t
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                t.label,
                                color = if (selected) p.ink else p.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                count.toString(),
                                color = if (selected) p.onSecondary else p.soft,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (selected) p.secondary else p.chip)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        listTitle,
                        color = if (attentionOnly) Amber else p.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    SortMenu(sort) { focus.clearFocus(); sort = it }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = hPad, end = hPad, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = when {
                                q.isNotEmpty() -> "Ничего не найдено"
                                attentionOnly -> "Нет продуктов, требующих внимания"
                                tab == MainTab.WEIGH -> "Взвешивать пока нечего"
                                tab == MainTab.ARCHIVE -> "Архив пуст"
                                else -> "Пока нет продуктов"
                            },
                            text = when {
                                q.isNotEmpty() -> "Измените поисковый запрос."
                                attentionOnly -> "Все партии в порядке."
                                tab == MainTab.WEIGH -> "Здесь появятся партии, у которых подошёл срок замера."
                                tab == MainTab.ARCHIVE -> "Завершённые партии появятся здесь."
                                else -> "Добавьте первый продукт, чтобы начать журнал."
                            },
                            showAdd = q.isEmpty() && !attentionOnly && tab == MainTab.PRODUCTS,
                            onAdd = onAdd,
                        )
                    }
                } else {
                    items(filtered, key = { it.first.id }) { row ->
                        BatchCard(
                            batch = row.first,
                            onClick = { focus.clearFocus(); onSelect(row.first) },
                            onWeigh = { focus.clearFocus(); onWeigh(row.first) },
                            nowEpochMs = now,
                        )
                    }
                    item(key = "footnote") {
                        Text(
                            "Нажмите на продукт, чтобы открыть подробности",
                            color = p.muted,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    description: String,
    tint: Color,
    bg: Color,
    border: Color,
    onClick: () -> Unit,
    badge: String? = null,
    badgeBg: Color = Color.Transparent,
    badgeInk: Color = Color.Transparent,
) {
    val p = LocalAppPalette.current
    Box(Modifier.size(48.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
        }
        if (badge != null) {
            val bs = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(bs)
                    .background(badgeBg)
                    .border(2.dp, p.background, bs)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = badgeInk, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val p = LocalAppPalette.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(47.dp)
            .clip(shape)
            .background(p.field)
            .border(1.dp, if (focused) p.selectedLine else p.divider, shape)
            .padding(start = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = p.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = p.ink, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Поиск...", color = p.muted, fontSize = 14.sp)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier.size(44.dp).clickable(role = Role.Button) { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Очистить поиск", tint = p.muted, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(13.dp))
        }
    }
}

@Composable
private fun SortMenu(current: BatchSort, onSelect: (BatchSort) -> Unit) {
    val p = LocalAppPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "${current.label} ▾",
            color = p.muted,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BatchSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, color = if (s == current) p.accent else p.ink) },
                    onClick = { open = false; onSelect(s) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String, showAdd: Boolean, onAdd: () -> Unit) {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.field)
            .border(1.dp, p.cardLine, shape)
            .padding(horizontal = 15.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(text, color = p.body, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
        if (showAdd) {
            Spacer(Modifier.height(17.dp))
            FcPrimaryButton(text = "Добавить продукт", onClick = onAdd)
        }
    }
}