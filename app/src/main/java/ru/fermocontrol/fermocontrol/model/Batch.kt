package ru.fermocontrol.fermocontrol.model

/**
 * Паспорт партии.
 */
data class Batch(
    val id: Long,
    val number: Int,
    val name: String,
    val category: String = "",
    val rawMaterial: String = "",
    val spices: String = "",
    val spiceItems: List<SpiceItem> = emptyList(),
    val casing: String = "",
	val productTypeId: String? = null,
    val startWeightGrams: Double,
    val targetLossPercent: Double,
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val note: String = "",
    val photoFileName: String? = null,
    val createdAtEpochMs: Long,
    val archived: Boolean = false,
    val logs: List<WeightLog> = emptyList(),
)

/**
 * Запись взвешивания.
 */
data class WeightLog(
    val atEpochMs: Long,
    val weightGrams: Double,
    val smellNote: String = "",
    val surfaceNote: String = "",
    val needsCheck: Boolean = false,
    val photoFileName: String? = null,
)