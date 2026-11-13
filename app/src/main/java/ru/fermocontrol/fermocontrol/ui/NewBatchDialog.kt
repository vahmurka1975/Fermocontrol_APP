package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember
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
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.Catalog
import ru.fermocontrol.fermocontrol.model.NewBatchDraft
import ru.fermocontrol.fermocontrol.model.SpiceCodec
import ru.fermocontrol.fermocontrol.ui.components.FcPrimaryButton
import ru.fermocontrol.fermocontrol.ui.components.FcSelect
import ru.fermocontrol.fermocontrol.ui.components.FcSheet
import ru.fermocontrol.fermocontrol.ui.components.FieldPair
import ru.fermocontrol.fermocontrol.ui.components.SpiceEditor
import ru.fermocontrol.fermocontrol.ui.components.SpiceRowsSaver
import ru.fermocontrol.fermocontrol.ui.components.LabeledInput
import ru.fermocontrol.fermocontrol.ui.theme.LocalAppPalette

private const val CUSTOM_LABEL = "Свой продукт"
private const val BASIS_PLACEHOLDER = "Выберите сырьё"
private const val BASIS_ERROR = "Выберите сырьё"

private fun parseNum(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun formatNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * Начальное сырьё. Для партии с сохранённым сырьём берём его.
 * Если сырья нет и позиция каталога требует выбора (Балык, Вырезка), оставляем пусто.
 * В остальных случаях подставляем первое значение списка.
 */
private fun initialBasis(initial: Batch?): String {
    val saved = initial?.rawMaterial.orEmpty()
    if (saved.isNotBlank()) return saved
    val item = Catalog.byId(initial?.productTypeId)
    return if (item != null && item.rawMaterial == null) "" else Catalog.rawMaterials.first()
}

/** Панель «Новый продукт» и «Редактировать паспорт»: все параметры в одном списке, сначала вес. */
@Composable
fun NewBatchDialog(
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (NewBatchDraft) -> Unit,
    initial: Batch? = null,
) {
    val p = LocalAppPalette.current
    val editing = initial != null
    val weightLocked = initial != null && initial.logs.isNotEmpty()
    var productTypeId by rememberSaveable { mutableStateOf(initial?.productTypeId.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var weight by rememberSaveable {
        mutableStateOf(initial?.let { formatNum(it.startWeightGrams) } ?: "")
    }
    var target by rememberSaveable {
        mutableStateOf(initial?.let { formatNum(it.targetLossPercent) } ?: "35")
    }
    var category by rememberSaveable {
        mutableStateOf(initial?.category.orEmpty().ifBlank { Catalog.categories.first() })
    }
    var basis by rememberSaveable { mutableStateOf(initialBasis(initial)) }
    val initialRecipe = remember { SpiceCodec.decode(initial?.spices.orEmpty()) }
    var nitrite by rememberSaveable { mutableStateOf(initialRecipe.nitrite) }
    var spiceRows by rememberSaveable(stateSaver = SpiceRowsSaver) { mutableStateOf(initialRecipe.rows) }
    var casing by rememberSaveable {
        mutableStateOf(initial?.casing.orEmpty().ifBlank { Catalog.casings.first() })
    }
    var temp by rememberSaveable {
        mutableStateOf(initial?.temperatureC?.let { formatNum(it) }.orEmpty())
    }
    var humidity by rememberSaveable {
        mutableStateOf(initial?.humidityPercent?.let { formatNum(it) }.orEmpty())
    }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val weightV = parseNum(weight)
    val targetV = parseNum(target)
    val tempV = parseNum(temp)
    val humV = parseNum(humidity)

    val nameErr = if (name.isBlank()) "Укажите название продукта" else null
    val weightErr =
        if (weightV == null || weightV <= 0.0) "Введите вес в граммах, больше 0" else null
    val targetErr =
        if (targetV == null || targetV < 1.0 || targetV > 80.0) "Усушка от 1 до 80 %" else null
    val basisErr = if (basis.isBlank()) BASIS_ERROR else null
    val tempErr =
        if (temp.isNotBlank() && (tempV == null || tempV < -30.0 || tempV > 80.0)) "От −30 до 80 °C" else null
    val humErr =
        if (humidity.isNotBlank() && (humV == null || humV < 0.0 || humV > 100.0)) "От 0 до 100 %" else null
    val spicesOk = SpiceCodec.isValid(nitrite, spiceRows)
    val valid = listOf(
        nameErr,
        weightErr,
        targetErr,
        basisErr,
        tempErr,
        humErr
    ).all { it == null } && spicesOk

    val pickedItem = Catalog.byId(productTypeId)
    val pickOptions = listOf(CUSTOM_LABEL) + Catalog.items.map { it.name }

    fun pick(label: String) {
        val item = Catalog.items.firstOrNull { it.name == label }
        if (item == null) {
            productTypeId = ""
            return
        }
        // Название подставляем, только если пользователь его не менял.
        val previous = pickedItem?.name
        if (name.isBlank() || name == previous) name = item.name

        productTypeId = item.id
        category = item.category
        // Если у позиции нет сырья (Балык, Вырезка), прежнее значение не переносим.
        basis = item.rawMaterial.orEmpty()
        target = formatNum(item.targetLossPercent)
        casing = item.casing ?: casing
    }

    FcSheet(
        title = if (editing) "Редактировать паспорт" else "Новый продукт",
        subtitle = initial?.let { "Партия №${it.number}" }
            ?: "Начните с веса. Остальные параметры можно заполнить сразу.",
        onDismiss = onDismiss,
        dismissible = !saving,
    ) {
        if (error != null) {
            Text(
                error,
                color = p.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        FcSelect(
            "Позиция из каталога",
            pickedItem?.name ?: CUSTOM_LABEL,
            pickOptions,
            { pick(it) },
            Modifier,
        )

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
                    enabled = !saving && !weightLocked,
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
        if (weightLocked) {
            Text(
                "Стартовый вес нельзя менять после первого взвешивания.",
                color = p.muted,
                fontSize = 11.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        FieldPair(
            first = { m ->
                FcSelect(
                    "Категория", category, Catalog.categories,
                    {
                        category = it
                        // Позиция другой категории больше не соответствует выбору.
                        if (pickedItem != null && pickedItem.category != it) productTypeId = ""
                    },
                    m,
                )
            },
            second = { m ->
                FcSelect(
                    "Сырьё",
                    basis.ifEmpty { BASIS_PLACEHOLDER },
                    Catalog.rawMaterials,
                    { basis = it },
                    m,
                )
            },
        )
        if (submitted && basisErr != null) {
            Text(
                "Выберите сырьё",
                color = p.danger,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        SpiceEditor(
            nitrite = nitrite,
            onNitrite = { nitrite = it },
            rows = spiceRows,
            onRows = { spiceRows = it },
            enabled = !saving,
            submitted = submitted,
            startWeightGrams = weightV,
            legacy = initialRecipe.legacy,
        )
        FcSelect("Оболочка", casing, Catalog.casings, { casing = it }, Modifier.fillMaxWidth())
        FieldPair(
            first = { m ->
                LabeledInput(
                    label = "Температура, °C",
                    value = temp,
                    onValueChange = { temp = it },
                    modifier = m,
                    placeholder = "Не указана",
                    enabled = !saving,
                    keyboardType = KeyboardType.Decimal,
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

        if (!editing) {
            Text(
                "Номер продукта назначится автоматически.",
                color = p.muted,
                fontSize = 11.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

        }

        if (submitted && !spicesOk) {
            Text(
                "Проверьте нитритную соль и специи",
                color = p.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        FcPrimaryButton(
            text = when {
                saving -> "Сохранение…"
                editing -> "Сохранить изменения"
                else -> "Добавить продукт"
            },
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
                        spices = SpiceCodec.encode(nitrite, spiceRows),
                        casing = casing,
                        productTypeId = productTypeId.ifEmpty { null },
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