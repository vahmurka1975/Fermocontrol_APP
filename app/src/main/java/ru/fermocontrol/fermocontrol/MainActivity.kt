package ru.fermocontrol.fermocontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.WeightLog
import ru.fermocontrol.fermocontrol.ui.MainScreen
import ru.fermocontrol.fermocontrol.ui.theme.FermocontrolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FermocontrolTheme {
                MainScreen(
                    batches = demoSampleBatches,
                    onSelect = { /* TODO: детали партии */ },
                    onWeigh = { /* TODO: взвешивание партии */ },
                    onAdd = { /* TODO: экран "Новый продукт" */ },
                    onSettings = { /* TODO: экран "Настройки" */ },
                    onProfile = { /* TODO: экран "Профиль" */ },
                )
            }
        }
    }
}

// Временные демо-данные, пока не подключено реальное хранилище.
private val demoSampleBatches = listOf(
    Batch(
        id = 1, number = 1, name = "Говядина вяленая",
        category = "Вяленое мясо", rawMaterial = "Говядина",
        startWeightGrams = 2500.0, targetLossPercent = 35.0,
        createdAtEpochMs = System.currentTimeMillis() - 5L * 86_400_000L,
        logs = listOf(
            WeightLog(
                atEpochMs = System.currentTimeMillis() - 3L * 86_400_000L,
                weightGrams = 2140.0,
            ),
            WeightLog(
                atEpochMs = System.currentTimeMillis() - 1L * 86_400_000L,
                weightGrams = 1980.0,
                needsCheck = true,
            ),
        ),
    ),
    Batch(
        id = 2, number = 2, name = "Колбаса сыровяленая",
        category = "Колбаса", rawMaterial = "Свинина + говядина",
        startWeightGrams = 3200.0, targetLossPercent = 30.0,
        createdAtEpochMs = System.currentTimeMillis() - 9L * 86_400_000L,
        logs = listOf(
            WeightLog(
                atEpochMs = System.currentTimeMillis() - 2L * 86_400_000L,
                weightGrams = 2470.0,
            ),
        ),
    ),
    Batch(
        id = 3, number = 3, name = "Сыр твёрдый",
        category = "Сыр", rawMaterial = "Молоко коровье",
        startWeightGrams = 1500.0, targetLossPercent = 18.0,
        createdAtEpochMs = System.currentTimeMillis() - 20L * 86_400_000L,
        logs = emptyList(),
        archived = true,
    ),
)