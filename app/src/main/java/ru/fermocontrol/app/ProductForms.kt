package ru.fermocontrol.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val bases = listOf("Свинина", "Говядина", "Птица", "Баранина", "Смешанное мясо", "Рыба", "Сыр", "Овощи", "Другая основа")
private val casings = listOf("Без оболочки", "Натуральная", "Коллагеновая", "Фиброузная", "Полиамидная", "Другая оболочка")
private val spiceOptions = listOf("Без специй", "Чёрный перец", "Белый перец", "Чеснок", "Паприка", "Кориандр", "Мускатный орех", "Тмин", "Готовая смесь", "Другие специи")
private val observationOptions = linkedMapOf(
    "Запах" to listOf("Не оценивал", "Обычный для продукта", "Кисловатый", "Резкий неприятный", "Гнилостный", "Другой"),
    "Поверхность" to listOf("Не оценивал", "Сухая", "Слегка влажная", "Липкая", "Слизь", "Белый налёт", "Цветной налёт", "Другая"),
    "Цвет" to listOf("Не оценивал", "Обычный для продукта", "Потемнение", "Неравномерный", "Необычные пятна", "Другой"),
    "Плотность" to listOf("Не оценивал", "Мягкая", "Упругая", "Плотная", "Очень твёрдая снаружи", "Другая")
)

@Composable
private fun FormDialog(title: String, busy: Boolean, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy
    )) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier
            .fillMaxWidth().padding(12.dp).heightIn(max = 720.dp)) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
        }
    }
}

@Composable
private fun ChoiceCapsule(label: String, value: String, options: List<String>, enabled: Boolean,
    onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(value.ifBlank { "Выбрать" })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option); expanded = false
                }) }
            }
        }
    }
}

@Composable
fun AddProductDialog(number: Int, busy: Boolean, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var basis by rememberSaveable { mutableStateOf("") }
    var casing by rememberSaveable { mutableStateOf("") }
    var spices by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var weight by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var spiceOpen by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val parsedWeight = parsePositiveNumber(weight)
    val parsedTarget = parsePositiveNumber(target)?.takeIf { it < 100.0 }
    val valid = name.isNotBlank() && basis.isNotBlank() && casing.isNotBlank() &&
        spices.isNotEmpty() && parsedWeight != null && parsedTarget != null
    FormDialog("Новый продукт · №$number", busy, onDismiss) {
        Text("Номер и время добавления присваиваются автоматически.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название продукта") },
            singleLine = true, enabled = !busy, isError = submitted && name.isBlank(), modifier = Modifier.fillMaxWidth())
        ChoiceCapsule("Основа продукта", basis, bases, !busy) { basis = it }
        ChoiceCapsule("Оболочка", casing, casings, !busy) { casing = it }
        OutlinedButton(onClick = { spiceOpen = !spiceOpen }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Специи: " + spices.joinToString().ifBlank { "выбрать" })
        }
        if (spiceOpen) {
            Text("Можно выбрать несколько вариантов", style = MaterialTheme.typography.bodySmall)
            spiceOptions.forEach { option ->
                FilterChip(selected = option in spices, enabled = !busy, onClick = {
                    spices = when {
                        option in spices -> ArrayList(spices.filterNot { it == option })
                        option == "Без специй" -> arrayListOf(option)
                        else -> ArrayList(spices.filterNot { it == "Без специй" } + option)
                    }
                }, label = { Text(option) })
            }
        }
        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Начальный вес, г") },
            singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = submitted && parsedWeight == null, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Целевая потеря веса, %") },
            singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = submitted && parsedTarget == null, modifier = Modifier.fillMaxWidth())
        Text("Цель задаётся по вашему проверенному рецепту. Потеря веса и внешний вид не подтверждают безопасность продукта.",
            style = MaterialTheme.typography.bodySmall)
        if (submitted && !valid) Text("Заполните все поля. Вес должен быть больше нуля, цель — больше 0 и меньше 100%.",
            color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            submitted = true
            if (valid) onSave(Product(number = number, name = name.trim(), basis = basis,
                spices = spices.toList(), casing = casing, initialWeight = parsedWeight!!, targetLoss = parsedTarget!!))
        }) { Text(if (busy) "Сохранение…" else "Добавить продукт") }
        TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
    }
}

@Composable
fun WeighProductDialog(product: Product, busy: Boolean, onDismiss: () -> Unit, onSave: (Weighing) -> Unit) {
    var weight by rememberSaveable(product.id) { mutableStateOf("") }
    var smell by rememberSaveable(product.id) { mutableStateOf("Не оценивал") }
    var surface by rememberSaveable(product.id) { mutableStateOf("Не оценивал") }
    var color by rememberSaveable(product.id) { mutableStateOf("Не оценивал") }
    var texture by rememberSaveable(product.id) { mutableStateOf("Не оценивал") }
    var note by rememberSaveable(product.id) { mutableStateOf("") }
    var submitted by rememberSaveable(product.id) { mutableStateOf(false) }
    val parsed = parsePositiveNumber(weight)
    FormDialog("Взвешивание · №${product.number}", busy, onDismiss) {
        Text(product.name, style = MaterialTheme.typography.titleMedium)
        Text("Предыдущий вес: ${grams(product.currentWeight)} г")
        Text("Дата и время запишутся автоматически при сохранении.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Текущий вес, г") },
            singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = submitted && parsed == null, modifier = Modifier.fillMaxWidth())
        ChoiceCapsule("Запах", smell, observationOptions.getValue("Запах"), !busy) { smell = it }
        ChoiceCapsule("Поверхность", surface, observationOptions.getValue("Поверхность"), !busy) { surface = it }
        ChoiceCapsule("Цвет", color, observationOptions.getValue("Цвет"), !busy) { color = it }
        ChoiceCapsule("Плотность", texture, observationOptions.getValue("Плотность"), !busy) { texture = it }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Заметка, необязательно") },
            enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
        if (parsed != null && parsed > product.currentWeight) Text(
            "Вес больше предыдущего. Проверьте значение; при необходимости его можно сохранить.",
            style = MaterialTheme.typography.bodySmall)
        if (submitted && parsed == null) Text("Введите вес больше нуля", color = MaterialTheme.colorScheme.error)
        if (smell in listOf("Резкий неприятный", "Гнилостный") || surface in listOf("Слизь", "Цветной налёт")) {
            Text("Есть настораживающие признаки. Не пробуйте продукт на вкус для проверки безопасности.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            submitted = true
            if (parsed != null) onSave(Weighing(weight = parsed,
                observations = linkedMapOf("Запах" to smell, "Поверхность" to surface, "Цвет" to color, "Плотность" to texture),
                note = note.trim()))
        }) { Text(if (busy) "Сохранение…" else "Сохранить взвешивание") }
        TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
    }
}

@Composable
fun ProductDetailsDialog(product: Product, onDismiss: () -> Unit) {
    FormDialog("Журнал продукта", false, onDismiss) {
        Text((if (product.archivedAt == null) "№${product.number}" else "Бывший №${product.number}") +
            " · ${product.name}", style = MaterialTheme.typography.titleLarge)
        if (product.archivedAt != null) Text("Архив: номер освобождён ${dateTime(product.archivedAt)}")
        Text("Основа: ${product.basis}")
        Text("Специи: ${product.spices.joinToString()}")
        Text("Оболочка: ${product.casing}")
        Text("Добавлен: ${dateTime(product.startedAt)} · ${grams(product.initialWeight)} г")
        Text("Цель: ${decimal(product.targetLoss)}% · ${grams(product.targetWeight)} г")
        HorizontalDivider()
        if (product.weighings.isEmpty()) Text("Взвешиваний пока нет")
        product.weighings.asReversed().forEach { record ->
            Text(dateTime(record.at), style = MaterialTheme.typography.titleSmall)
            Text("Вес: ${grams(record.weight)} г")
            record.observations.forEach { (key, value) -> Text("$key: $value") }
            if (record.note.isNotBlank()) Text("Заметка: ${record.note}")
            HorizontalDivider()
        }
        Text("Журнал не является заключением о безопасности продукта.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
}
