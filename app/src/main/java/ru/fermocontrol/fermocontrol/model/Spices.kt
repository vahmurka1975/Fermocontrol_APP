package ru.fermocontrol.fermocontrol.model

const val NITRITE_SALT = "Нитритная соль"
const val MAX_SPICE_GRAMS_PER_KG = 100.0

/** Специя в паспорте: название и граммы на 1 кг сырья. */
data class SpiceItem(
    val name: String,
    val gramsPerKg: Double,
)

fun formatGrams(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().replace('.', ',')

fun List<SpiceItem>.toSummary(): String =
    joinToString(", ") { "${it.name} ${formatGrams(it.gramsPerKg)} г/кг" }