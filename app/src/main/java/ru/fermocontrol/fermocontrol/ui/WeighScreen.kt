@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.fermocontrol.fermocontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fermocontrol.fermocontrol.model.Batch
import java.util.Locale

@Composable
fun WeighScreen(
    batch: Batch,
    onBack: () -> Unit,
    onSave: (Double) -> Unit,
    saving: Boolean = false,
    errorMessage: String? = null,
) {
    var weightText by remember(batch.id) {
        mutableStateOf("")
    }

    val weight = weightText
        .replace(',', '.')
        .trim()
        .toDoubleOrNull()

    val inputError = when {
        weightText.isBlank() -> null
        weight == null -> "Введите число"
        weight <= 0.0 -> "Вес должен быть больше нуля"
        else -> null
    }

    val lossPercent = if (
        weight != null &&
        batch.startWeightGrams > 0.0
    ) {
        (batch.startWeightGrams - weight) /
            batch.startWeightGrams * 100.0
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Взвешивание")
                },
                navigationIcon = {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !saving,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Назад")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = batch.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Партия №${batch.number}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoRow(
                        label = "Начальный вес",
                        value = "${formatNumber(batch.startWeightGrams)} г",
                    )

                    InfoRow(
                        label = "Целевая усушка",
                        value = "${formatNumber(batch.targetLossPercent)}%",
                    )

                    batch.logs.lastOrNull()?.let { lastLog ->
                        InfoRow(
                            label = "Последний вес",
                            value = "${formatNumber(lastLog.weightGrams)} г",
                        )
                    }
                }
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Текущий вес, г")
                },
                placeholder = {
                    Text("Например, 1980 или 1980,5")
                },
                singleLine = true,
                enabled = !saving,
                isError = inputError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
            )

            if (inputError != null) {
                Text(
                    text = inputError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            if (lossPercent != null) {
                val status = when {
                    lossPercent < 0.0 ->
                        "Вес больше начального. Проверь значение."

                    lossPercent >= batch.targetLossPercent ->
                        "Целевая усушка достигнута."

                    else ->
                        "До цели осталось ${
                            formatNumber(
                                batch.targetLossPercent - lossPercent,
                            )
                        }%"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Текущая усушка: ${
                                formatNumber(lossPercent)
                            }%",
                            fontWeight = FontWeight.Medium,
                        )

                        Text(
                            text = status,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (weight != null && weight > 0.0) {
                        onSave(weight)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = weight != null &&
                    weight > 0.0 &&
                    inputError == null &&
                    !saving,
            ) {
                Text(
                    text = if (saving) {
                        "Сохранение..."
                    } else {
                        "Сохранить взвешивание"
                    },
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            ) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = value,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatNumber(value: Double): String {
    return String.format(
        Locale.US,
        "%.2f",
        value,
    )
        .trimEnd('0')
        .trimEnd('.')
}