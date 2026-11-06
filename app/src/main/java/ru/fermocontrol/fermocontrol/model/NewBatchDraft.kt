package ru.fermocontrol.fermocontrol.model

data class NewBatchDraft(
    val name: String,
    val startWeightGrams: Double,
    val targetLossPercent: Double,
    val category: String = "",
    val rawMaterial: String = "",
    val spices: String = "",
    val casing: String = "",
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val note: String = "",
)