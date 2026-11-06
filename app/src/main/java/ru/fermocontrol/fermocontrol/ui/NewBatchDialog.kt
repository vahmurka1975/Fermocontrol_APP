package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.fermocontrol.fermocontrol.model.NewBatchDraft

@Composable
fun NewBatchDialog(
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (NewBatchDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var rawMaterial by rememberSaveable { mutableStateOf("") }
    var spices by rememberSaveable { mutableStateOf("") }
    var casing by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var targetLoss by rememberSaveable { mutableStateOf("35") }
    var temperature by rememberSaveable { mutableStateOf("") }
    var humidity by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val scroll = rememberScrollState()

    LaunchedEffect(error) {
        if (error != null) {
            scroll.animateScrollTo(0)
        }
    }

    val weightValue = parseDecimal(weight)
    val lossValue = parseDecimal(targetLoss)
    val temperatureValue = parseDecimal(temperature)
    val humidityValue = parseDecimal(humidity)

    val nameError =
        if (name.isBlank()) "Укажите название продукта" else null

    val weightError =
        if (weightValue == null || weightValue <= 0.0) {
            "Введите вес в граммах, больше 0"
        } else {
            null
        }

    val lossError =
        if (lossValue == null || lossValue <= 0.0 || lossValue >= 100.0) {
            "Усушка должна быть больше 0 и меньше 100"
        } else {
            null
        }

    val temperatureError =
        if (temperature.isNotBlank() && temperatureValue == null) {
            "Введите число или оставьте поле пустым"
        } else {
            null
        }

    val humidityError =
        if (
            humidity.isNotBlank() &&
            (humidityValue == null || humidityValue !in 0.0..100.0)
        ) {
            "Введите влажность от 0 до 100"
        } else {
            null
        }

    val valid = listOf(
        nameError,
        weightError,
        lossError,
        temperatureError,
        humidityError,
    ).all { it == null }

    AlertDialog(
        onDismissRequest = {
            if (!saving) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !saving,
            dismissOnClickOutside = !saving,
        ),
        title = {
            Text("Новый продукт")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Номер партии и дата закладки назначаются при сохранении.",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                NewBatchField(
                    label = "Название *",
                    value = name,
                    onValueChange = { name = it },
                    enabled = !saving,
                    error = if (submitted) nameError else null,
                )

                NewBatchField(
                    label = "Категория",
                    value = category,
                    onValueChange = { category = it },
                    enabled = !saving,
                )

                NewBatchField(
                    label = "Сырьё",
                    value = rawMaterial,
                    onValueChange = { rawMaterial = it },
                    enabled = !saving,
                )

                NewBatchField(
                    label = "Специи",
                    value = spices,
                    onValueChange = { spices = it },
                    enabled = !saving,
                )

                NewBatchField(
                    label = "Оболочка",
                    value = casing,
                    onValueChange = { casing = it },
                    enabled = !saving,
                )

                NewBatchField(
                    label = "Начальный вес, г *",
                    value = weight,
                    onValueChange = { weight = it },
                    enabled = !saving,
                    decimal = true,
                    error = if (submitted) weightError else null,
                )

                NewBatchField(
                    label = "Целевая усушка, % *",
                    value = targetLoss,
                    onValueChange = { targetLoss = it },
                    enabled = !saving,
                    decimal = true,
                    error = if (submitted) lossError else null,
                )

                // Текстовая клавиатура позволяет вводить минус.
                NewBatchField(
                    label = "Температура камеры, °C",
                    value = temperature,
                    onValueChange = { temperature = it },
                    enabled = !saving,
                    error = if (submitted) temperatureError else null,
                )

                NewBatchField(
                    label = "Влажность, %",
                    value = humidity,
                    onValueChange = { humidity = it },
                    enabled = !saving,
                    decimal = true,
                    error = if (submitted) humidityError else null,
                )

                NewBatchField(
                    label = "Заметка",
                    value = note,
                    onValueChange = { note = it },
                    enabled = !saving,
                    multiline = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    submitted = true

                    if (valid && weightValue != null && lossValue != null) {
                        onSave(
                            NewBatchDraft(
                                name = name.trim(),
                                startWeightGrams = weightValue,
                                targetLossPercent = lossValue,
                                category = category.trim(),
                                rawMaterial = rawMaterial.trim(),
                                spices = spices.trim(),
                                casing = casing.trim(),
                                temperatureC = temperatureValue,
                                humidityPercent = humidityValue,
                                note = note.trim(),
                            )
                        )
                    }
                },
            ) {
                Text(if (saving) "Сохранение…" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss,
            ) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun NewBatchField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String? = null,
    decimal: Boolean = false,
    multiline: Boolean = false,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = !multiline,
            minLines = if (multiline) 2 else 1,
            maxLines = if (multiline) 4 else 1,
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) {
                    KeyboardType.Decimal
                } else {
                    KeyboardType.Text
                },
            ),
        )

        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun parseDecimal(value: String): Double? =
    value.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }