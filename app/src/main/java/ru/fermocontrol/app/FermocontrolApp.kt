package ru.fermocontrol.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FermocontrolApp(
    openProductId: String? = null,
    onConsumeOpenProductId: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val store = remember { ProductStore(context) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var writeError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var onlyDueWeighing by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var weighingId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        loading = true
        loadError = null
        try {
            products = withContext(Dispatchers.IO) { store.load() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            loadError = "Не удалось прочитать данные. Они не будут перезаписаны. " +
                (error.localizedMessage ?: "Ошибка чтения")
        } finally { loading = false }
    }

    LaunchedEffect(openProductId, loading, products) {
        if (!loading && openProductId != null) {
            val found = products.find { it.id == openProductId }
            if (found != null) {
                archived = found.archivedAt != null
                onlyDueWeighing = false
                detailsInitialTab = 0
                detailsId = found.id
            }
            onConsumeOpenProductId()
        }
    }

    fun persist(next: List<Product>, onSuccess: () -> Unit) {
        if (busy || loading || loadError != null) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                products = next
                ReminderScheduler.checkAndNotifyIfNeeded(context, force = false)
                onSuccess()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                writeError = "Не удалось сохранить изменения. Повторите попытку. " +
                    (error.localizedMessage ?: "Ошибка записи")
            } finally { busy = false }
        }
    }

    val activeCount = products.count { it.archivedAt == null }
    val archiveCount = products.size - activeCount
    val dueWeighingCount = products.count { needsWeighing(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!loading && loadError == null && !archived) {
                ExtendedFloatingActionButton(
                    onClick = { if (!busy) adding = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Text("+  Добавить продукт") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("ФермоКонтроль", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                BackupPanel(products = products, store = store, available = !loading && !busy,
                    journalAvailable = loadError == null,
                    onBusy = { busy = it }, onRestored = { restored ->
                        products = restored
                        loadError = null
                        writeError = null
                        adding = false
                        weighingId = null
                        detailsId = null
                        editingId = null
                        archiveId = null
                    })
            }
            Text("Дневник сыровяления", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (products.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по №, названию, основе, специям…") },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = { searchQuery = "" }) { Text("Сброс") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !archived && !onlyDueWeighing,
                    onClick = {
                        archived = false
                        onlyDueWeighing = false
                    },
                    label = { Text("Продукты · $activeCount") }
                )
                FilterChip(
                    selected = !archived && onlyDueWeighing,
                    onClick = {
                        archived = false
                        onlyDueWeighing = !onlyDueWeighing
                    },
                    label = { Text("Ждут взвешивания · $dueWeighingCount") }
                )
                FilterChip(
                    selected = archived,
                    onClick = {
                        archived = true
                        onlyDueWeighing = false
                    },
                    label = { Text("Архив · $archiveCount") }
                )
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadError != null -> Column(Modifier.padding(top = 24.dp)) {
                    Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { attempt++ }) { Text("Повторить чтение") }
                }
                else -> {
                    val baseList = when {
                        archived -> products.filter { it.archivedAt != null }.sortedByDescending { it.archivedAt }
                        onlyDueWeighing -> products.filter { needsWeighing(it) }.sortedBy { lastWeighingOrStartTime(it) }
                        else -> products.filter { it.archivedAt == null }.sortedBy { it.number }
                    }
                    val visible = baseList.filter { matchesProductQuery(it, searchQuery) }

                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val emptyTitle = when {
                                    searchQuery.isNotBlank() -> "По запросу ничего не найдено"
                                    onlyDueWeighing -> "Все активные продукты взвешены вовремя"
                                    archived -> "В архиве пока пусто"
                                    else -> "Добавьте первый продукт"
                                }
                                val emptySubtitle = when {
                                    searchQuery.isNotBlank() -> "Попробуйте изменить строку поиска или сбросить фильтр."
                                    onlyDueWeighing -> "Продукты попадают сюда, если с последнего взвешивания прошло 3 и более суток."
                                    archived -> "Здесь сохраняются извлечённые продукты и их журналы."
                                    else -> "Записывайте вес и наблюдения в одном месте."
                                }
                                Text(emptyTitle, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(emptySubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(visible, key = { it.id }) { product ->
                                ProductCard(
                                    product = product,
                                    busy = busy,
                                    onDetails = {
                                        detailsInitialTab = 0
                                        detailsId = product.id
                                    },
                                    onChart = {
                                        detailsInitialTab = 1
                                        detailsId = product.id
                                    },
                                    onWeigh = { weighingId = product.id },
                                    onArchive = { archiveId = product.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (!loading && loadError == null) {
        if (adding) AddProductDialog(firstFreeNumber(products), busy,
            onDismiss = { if (!busy) adding = false },
            onSave = { draft ->
                val product = draft.copy(number = firstFreeNumber(products))
                persist(products + product) { adding = false }
            })
        val weighing = products.find { it.id == weighingId && it.archivedAt == null }
        if (weighing != null) WeighProductDialog(weighing, busy,
            onDismiss = { if (!busy) weighingId = null }, onSave = { record ->
                val next = products.map { if (it.id == weighing.id)
                    it.copy(weighings = it.weighings + record) else it }
                persist(next) { weighingId = null }
            })
        val editing = products.find { it.id == editingId && it.archivedAt == null }
        if (editing != null) {
            EditProductDialog(
                product = editing,
                busy = busy,
                onDismiss = { if (!busy) editingId = null },
                onSave = { updated ->
                    persist(products.map { if (it.id == updated.id) updated else it }) {
                        editingId = null
                    }
                }
            )
        }
        products.find { it.id == detailsId }?.let { product ->
            val nextNumber = nextActiveNumberForRestore(products, product.id)
            ProductDetailsDialog(
                product = product,
                nextActiveNumber = nextNumber,
                initialTab = detailsInitialTab,
                busy = busy,
                onEdit = {
                    val targetId = product.id
                    detailsId = null
                    editingId = targetId
                },
                onDeleteWeighing = { removeIndex ->
                    val updated = product.copy(
                        weighings = product.weighings.filterIndexed { idx, _ -> idx != removeIndex }
                    )
                    persist(products.map { if (it.id == product.id) updated else it }) {}
                },
                onUnarchive = {
                    persist(restoreArchivedProduct(products, product.id)) {
                        detailsId = null
                        archived = false
                    }
                },
                onDismiss = { if (!busy) detailsId = null }
            )
        }
        val archive = products.find { it.id == archiveId && it.archivedAt == null }
        if (archive != null) AlertDialog(
            onDismissRequest = { if (!busy) archiveId = null },
            title = { Text("Извлечь продукт №${archive.number}?") },
            text = { Text("«${archive.name}» перейдёт в архив вместе с журналом. Номер ${archive.number} " +
                "освободится для нового продукта. Это действие само по себе не подтверждает пищевую безопасность.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val at = System.currentTimeMillis()
                persist(products.map { if (it.id == archive.id) it.copy(archivedAt = at) else it }) {
                    archiveId = null
                }
            }) { Text("Извлечь в архив") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { archiveId = null }) { Text("Отмена") } }
        )
    }
    if (writeError != null) AlertDialog(onDismissRequest = { writeError = null },
        title = { Text("Ошибка сохранения") }, text = { Text(writeError.orEmpty()) },
        confirmButton = { TextButton(onClick = { writeError = null }) { Text("Понятно") } })
}

@Composable
private fun ProductCard(
    product: Product,
    busy: Boolean,
    onDetails: () -> Unit,
    onChart: () -> Unit,
    onWeigh: () -> Unit,
    onArchive: () -> Unit
) {
    val due = needsWeighing(product)
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (product.archivedAt == null) "№${product.number} · В работе"
                    else "Архив · бывший №${product.number}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isTargetReached(product)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Цель достигнута",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (due) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Пора взвесить",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${product.basis} · ${product.casing}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${grams(product.currentWeight)} г", style = MaterialTheme.typography.headlineSmall)
            Text("Потеря веса: ${decimal(product.lossPercent)}% · цель ${decimal(product.targetLoss)}%")
            LinearProgressIndicator(progress = {
                (product.lossPercent / product.targetLoss).toFloat().coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
            Text("Целевой вес: ${grams(product.targetWeight)} г",
                style = MaterialTheme.typography.bodySmall)
            if (product.archivedAt == null && product.lossPercent >= product.targetLoss) {
                Text("Цель по весу достигнута — это не оценка безопасности.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(if (product.archivedAt == null) "Начало: ${dateTime(product.startedAt)}"
                else "Извлечён: ${dateTime(product.archivedAt)}",
                style = MaterialTheme.typography.bodySmall)
            Text(
                weighingStatusText(product),
                style = MaterialTheme.typography.bodySmall,
                color = if (due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (product.archivedAt == null) {
                Button(onClick = onWeigh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Взвесить")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDetails) { Text("Журнал (${product.weighings.size})") }
                    TextButton(onClick = onChart) { Text("График") }
                }
                if (product.archivedAt == null) TextButton(onClick = onArchive, enabled = !busy) {
                    Text("Извлечь")
                }
            }
        }
    }
}