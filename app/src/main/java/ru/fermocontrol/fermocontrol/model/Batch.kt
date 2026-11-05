package ru.fermocontrol.fermocontrol.model

import java.time.Instant

/**
 * Паспорт партии (закладка). Соответствует записи "продукт" в HTML-макете.
 */
data class Batch(
    val id: Long,
    val number: Int,              // порядковый номер партии
    val name: String,             // название продукта
    val category: String = "",    // категория (вяленое мясо, колбаса, сыр...)
    val rawMaterial: String = "", // сырьё (говядина, свинина...)
    val spices: String = "",      // специи
    val casing: String = "",      // оболочка
    val startWeightGrams: Double, // начальный вес, г
    val targetLossPercent: Double,// целевая усушка, %
    val temperatureC: Double? = null, // температура камеры
    val humidityPercent: Double? = null, // влажность
    val note: String = "",
    val photoFileName: String? = null,
    val createdAtEpochMs: Long,   // дата закладки
    val archived: Boolean = false,
    val logs: List<WeightLog> = emptyList(),
)

/**
 * Запись взвешивания. Последняя запись — текущее состояние партии.
 */
data class WeightLog(
    val atEpochMs: Long,          // дата взвешивания
    val weightGrams: Double,      // текущий вес, г
    val smellNote: String = "",   // осмотр: запах
    val surfaceNote: String = "", // осмотр: поверхность
    val needsCheck: Boolean = false, // "требует проверки"
    val photoFileName: String? = null,
)