package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.NewBatchDraft
import ru.fermocontrol.fermocontrol.ui.components.CollapsibleSection
import ru.fermocontrol.fermocontrol.ui.components.FcPrimaryButton
import ru.fermocontrol.fermocontrol.ui.components.FcSelect
import ru.fermocontrol.fermocontrol.ui.components.FcSheet
import ru.fermocontrol.fermocontrol.ui.components.FieldPair
import ru.fermocontrol.fermocontrol.ui.components.LabeledInput
import ru.fermocontrol.fermocontrol.ui.theme.Danger
import ru.fermocontrol.fermocontrol.ui.theme.Muted

private val CATEGORIES = listOf("Сыровяление", "Ферментация овощей", "Сыроделие", "Другое")
private val BASES = listOf("Говядина", "Свинина", "Птица", "Рыба", "Сыр", "Овощи")
private val CASINGS = listOf("Айцел Премиум", "Коллагеновая", "Натуральная", "Чудо-рукав", "Без оболочки")

private fun parseNum(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Панель «Новый продукт» по макету v0.4.1: сначала вес, остальное в раскрываемом разделе. */
@Composable
fun NewBatchDialog(
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (NewBatchDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("35") }
    var category by rememberSaveable { mutableStateOf(CATEGORIES.first()) }
    var basis by rememberSaveable { mutableStateOf(BASES.first()) }
    var spices by rememberSaveable { mutableStateOf("") }
    var casing by rememberSaveable { mutableStateOf(CASINGS.first()) }
    var temp by rememberSaveable { mutableStateOf("") }
    var humidity by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val weightV = parseNum(weight)
    val targetV = parseNum(target)
    val tempV = parseNum(temp)
    val humV = parseNum(humidity)

    val nameErr = if (name.isBlank()) "Укажите название продукта" else null
    val weightErr = if (weightV == null || weightV <= 0.0) "Введите вес в граммах, больше 0" else null
    val targetErr = if (targetV == null || targetV < 1.0 || targetV > 80.0) "Усушка от 1 до 80 %" else null
    val tempErr = if (temp.isNotBlank() && (tempV == null || tempV < -30.0 || tempV > 80.0)) "От −30 до 80 °C" else null
    val humErr = if (humidity.isNotBlank() && (humV == null || humV < 0.0 || humV > 100.0)) "От 0 до 100 %" else null
    val valid = listOf(nameErr, weightErr, targetErr, tempErr, humErr).all { it == null }

    FcSheet(
        title = "Новый продукт",
        subtitle = "Начните с веса. Остальные параметры можно заполнить сразу.",
        onDismiss = onDismiss,
        dismissible = !saving,
    ) {
        if (error != null) {
            Text(error, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        LabeledInput(
            label = "Название продукта",
            value = name,
            onValueChange = { name = it.take(100) },
            placeholder = "Например, Брезаола Резерв",
            enabled = !saving,
            error = if (submitted) nameErr else null,
        )

        FieldPair(
            first = { m ->
                LabeledInput(
                    label = "Начальный вес, г",
                    value = weight,
                    onValueChange = { weight = it },
                    modifier = m,
                    placeholder = "1600",
                    enabled = !saving,
                    keyboardType = KeyboardType.Decimal,
                    error = if (submitted) weightErr else null,
                )
            },
            second = { m ->
                LabeledInput(
                    label = "Целевая усушка, %",
                    value = target,
                    onValueChange = { target = it },
                    modifier = m,
                    enabled = !saving,
                    keyboardType = KeyboardType.Decimal,
                    error = if (submitted) targetErr else null,
                )
            },
        )

        CollapsibleSection("Паспорт партии и климат") {
            FieldPair(
                first = { m -> FcSelect("Категория", category, CATEGORIES, { category = it }, m) },
                second = { m -> FcSelect("Сырьё", basis, BASES, { basis = it }, m) },
            )
            FieldPair(
                first = { m ->
                    LabeledInput(
                        label = "Посол и специи",
                        value = spices,
                        onValueChange = { spices = it.take(300) },
                        modifier = m,
                        placeholder = "По вашему рецепту",
                        enabled = !saving,
                    )
                },
                second = { m -> FcSelect("Оболочка", casing, CASINGS, { casing = it }, m) },
            )
            FieldPair(
                first = { m ->
                    LabeledInput(
                        label = "Температура, °C",
                        value = temp,
                        onValueChange = { temp = it },
                        modifier = m,
                        placeholder = "Не указана",
                        enabled = !saving,
                        error = if (submitted) tempErr else null,
                    )
                },
                second = { m ->
                    LabeledInput(
                        label = "Влажность, % RH",
                        value = humidity,
                        onValueChange = { humidity = it },
                        modifier = m,
                        placeholder = "Не указана",
                        enabled = !saving,
                        keyboardType = KeyboardType.Decimal,
                        error = if (submitted) humErr else null,
                    )
                },
            )
            LabeledInput(
                label = "Заметка по технологии",
                value = note,
                onValueChange = { note = it.take(5000) },
                placeholder = "Рецепт, особенности сырья, этапы…",
                enabled = !saving,
                singleLine = false,
                minHeight = 96.dp,
            )
        }

        Text(
            "Номер продукта назначится автоматически.",
            color = Muted,
            fontSize = 11.5.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        FcPrimaryButton(
            text = if (saving) "Сохранение…" else "Добавить продукт",
            enabled = !saving,
        ) {
            submitted = true
            if (valid && weightV != null && targetV != null) {
                onSave(
                    NewBatchDraft(
                        name = name.trim(),
                        startWeightGrams = weightV,
                        targetLossPercent = targetV,
                        category = category,
                        rawMaterial = basis,
                        spices = spices.trim(),
                        casing = casing,
                        temperatureC = tempV,
                        humidityPercent = humV,
                        note = note.trim(),
                    )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
