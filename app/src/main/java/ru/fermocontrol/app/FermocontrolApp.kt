package ru.fermocontrol.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun FermocontrolApp() {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ProductStore(context) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var writeError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var weighingId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        loading = true
        loadError = null
        try { products = withContext(Dispatchers.IO) { store.load() } }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            loadError = "Не удалось прочитать данные. Они не будут перезаписаны. " +
                (error.localizedMessage ?: "Ошибка чтения")
        } finally { loading = false }
    }

    fun persist(next: List<Product>, onSuccess: () -> Unit) {
        if (busy || loading || loadError != null) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(next) }
                products = next
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
                        archiveId = null
                    })
            }
            Text("Дневник сыровяления", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !archived, onClick = { archived = false },
                    label = { Text("Продукты · $activeCount") })
                FilterChip(selected = archived, onClick = { archived = true },
                    label = { Text("Архив · $archiveCount") })
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
                    val visible = if (archived) products.filter { it.archivedAt != null }
                        .sortedByDescending { it.archivedAt }
                    else products.filter { it.archivedAt == null }.sortedBy { it.number }
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (archived) "В архиве пока пусто" else "Добавьте первый продукт",
                                    style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(if (archived) "Здесь сохраняются извлечённые продукты и их журналы."
                                    else "Записывайте вес и наблюдения в одном месте.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(visible, key = { it.id }) { product ->
                                ProductCard(product, busy,
                                    onDetails = { detailsId = product.id },
                                    onWeigh = { weighingId = product.id },
                                    onArchive = { archiveId = product.id })
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
        val detail = products.find { it.id == detailsId }
        if (detail != null) ProductDetailsDialog(detail, onDismiss = { detailsId = null })
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
private fun ProductCard(product: Product, busy: Boolean,
    onDetails: () -> Unit, onWeigh: () -> Unit, onArchive: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (product.archivedAt == null) "№${product.number} · В работе"
                else "Архив · бывший №${product.number}",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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
            if (product.archivedAt == null) {
                Button(onClick = onWeigh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Взвесить")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDetails) { Text("Журнал") }
                if (product.archivedAt == null) TextButton(onClick = onArchive, enabled = !busy) {
                    Text("Извлечь")
                }
            }
        }
    }
}
