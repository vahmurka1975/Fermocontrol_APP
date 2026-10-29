package ru.fermocontrol.app

import java.util.UUID

data class Weighing(
    val weight: Double,
    val at: Long = System.currentTimeMillis(),
    val observations: Map<String, String> = emptyMap(),
    val note: String = ""
)

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val name: String,
    val basis: String,
    val spices: List<String>,
    val casing: String,
    val initialWeight: Double,
    val targetLoss: Double,
    val startedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,
    val weighings: List<Weighing> = emptyList()
) {
    val currentWeight: Double get() = weighings.lastOrNull()?.weight ?: initialWeight
    val lossPercent: Double get() = (initialWeight - currentWeight) / initialWeight * 100.0
    val targetWeight: Double get() = initialWeight * (1.0 - targetLoss / 100.0)
}

fun firstFreeNumber(products: List<Product>): Int {
    val used = products.filter { it.archivedAt == null }.map { it.number }.toSet()
    var number = 1
    while (number in used) number++
    return number
}

fun nextActiveNumberForRestore(products: List<Product>, productId: String): Int {
    val target = products.find { it.id == productId }
        ?: throw IllegalArgumentException("Продукт не найден")
    val others = products.filter { it.id != productId }
    val activeNumbers = others.filter { it.archivedAt == null }.map { it.number }.toSet()
    return if (target.number > 0 && target.number !in activeNumbers) {
        target.number
    } else {
        firstFreeNumber(others)
    }
}

fun restoreArchivedProduct(products: List<Product>, productId: String): List<Product> {
    val target = products.find { it.id == productId }
        ?: throw IllegalArgumentException("Продукт не найден")
    if (target.archivedAt == null) return products
    val assignedNumber = nextActiveNumberForRestore(products, productId)
    val restored = target.copy(number = assignedNumber, archivedAt = null)
    val updated = products.map { if (it.id == productId) restored else it }
    validateProducts(updated)
    return updated
}

fun parsePositiveNumber(text: String): Double? = text.trim().replace(',', '.')
    .toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

fun validateProducts(products: List<Product>) {
    require(products.map { it.id }.toSet().size == products.size) { "Повторяющийся ID продукта" }
    val active = products.filter { it.archivedAt == null }
    require(active.map { it.number }.toSet().size == active.size) { "Повторяющийся активный номер" }
    products.forEach { p ->
        require(p.id.isNotBlank() && p.name.isNotBlank() && p.number > 0)
        require(p.initialWeight.isFinite() && p.initialWeight > 0.0)
        require(p.targetLoss.isFinite() && p.targetLoss > 0.0 && p.targetLoss < 100.0)
        p.weighings.forEach { require(it.weight.isFinite() && it.weight > 0.0) }
    }
}
