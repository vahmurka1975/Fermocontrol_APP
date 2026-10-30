package ru.fermocontrol.app

import java.util.UUID

data class Weighing(
    val weight: Double,
    val at: Long = System.currentTimeMillis(),
    val observations: Map<String, String> = emptyMap(),
    val note: String = "",
    val temperature: Double? = null,
    val humidity: Double? = null,
    val photoPath: String? = null
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
    val weighings: List<Weighing> = emptyList(),
    val reminderIntervalDays: Int = 3,
    val targetTemp: Double? = null,
    val targetHumidity: Double? = null,
    val photoPath: String? = null
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

fun parseOptionalNumber(text: String, min: Double = -50.0, max: Double = 100.0): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }
}

fun validateProducts(products: List<Product>) {
    require(products.map { it.id }.toSet().size == products.size) { "Повторяющийся ID продукта" }
    val active = products.filter { it.archivedAt == null }
    require(active.map { it.number }.toSet().size == active.size) { "Повторяющийся активный номер" }
    products.forEach { p ->
        require(p.id.isNotBlank() && p.name.isNotBlank() && p.number > 0)
        require(p.initialWeight.isFinite() && p.initialWeight > 0.0)
        require(p.targetLoss.isFinite() && p.targetLoss > 0.0 && p.targetLoss < 100.0)
        require(p.reminderIntervalDays in 1..60) { "Интервал напоминаний должен быть от 1 до 60 суток" }
        p.targetTemp?.let { require(it.isFinite() && it in -30.0..60.0) { "Неверная температура" } }
        p.targetHumidity?.let { require(it.isFinite() && it in 1.0..100.0) { "Неверная влажность" } }
        p.weighings.forEach { w ->
            require(w.weight.isFinite() && w.weight > 0.0)
            w.temperature?.let { require(it.isFinite() && it in -30.0..60.0) { "Неверная температура взвешивания" } }
            w.humidity?.let { require(it.isFinite() && it in 1.0..100.0) { "Неверная влажность взвешивания" } }
        }
    }
}

data class ProductTemplate(
    val title: String,
    val defaultName: String,
    val basis: String,
    val casing: String,
    val spices: List<String>,
    val targetLoss: Double,
    val reminderIntervalDays: Int = 3,
    val targetTemp: Double = 12.0,
    val targetHumidity: Double = 75.0
)

val defaultProductTemplates: List<ProductTemplate> = listOf(
    ProductTemplate(
        title = "Шейка сыровяленая (Коппа)",
        defaultName = "Коппа классическая",
        basis = "Свинина",
        casing = "Коллагеновая",
        spices = listOf("Чёрный перец", "Чеснок", "Кориандр", "Мускатный орех"),
        targetLoss = 35.0,
        reminderIntervalDays = 3,
        targetTemp = 12.0,
        targetHumidity = 75.0
    ),
    ProductTemplate(
        title = "Карбонад / Ломо",
        defaultName = "Карбонад сыровяленый",
        basis = "Свинина",
        casing = "Коллагеновая",
        spices = listOf("Чёрный перец", "Паприка", "Чеснок"),
        targetLoss = 36.0,
        reminderIntervalDays = 3,
        targetTemp = 12.0,
        targetHumidity = 75.0
    ),
    ProductTemplate(
        title = "Брезаола (говядина)",
        defaultName = "Брезаола",
        basis = "Говядина",
        casing = "Коллагеновая",
        spices = listOf("Чёрный перец", "Чеснок", "Мускатный орех"),
        targetLoss = 38.0,
        reminderIntervalDays = 3,
        targetTemp = 12.0,
        targetHumidity = 75.0
    ),
    ProductTemplate(
        title = "Утиная грудка",
        defaultName = "Утиная грудка сыровяленая",
        basis = "Птица",
        casing = "Без оболочки",
        spices = listOf("Чёрный перец", "Кориандр", "Чеснок"),
        targetLoss = 32.0,
        reminderIntervalDays = 2,
        targetTemp = 11.0,
        targetHumidity = 75.0
    ),
    ProductTemplate(
        title = "Колбаса сыровяленая в натуральной череве",
        defaultName = "Домашняя сыровяленая колбаса",
        basis = "Смешанное мясо",
        casing = "Натуральная",
        spices = listOf("Чёрный перец", "Белый перец", "Чеснок", "Мускатный орех"),
        targetLoss = 38.0,
        reminderIntervalDays = 2,
        targetTemp = 12.0,
        targetHumidity = 76.0
    ),
    ProductTemplate(
        title = "Бастурма говяжья",
        defaultName = "Бастурма",
        basis = "Говядина",
        casing = "Без оболочки",
        spices = listOf("Паприка", "Чеснок", "Тмин", "Кориандр"),
        targetLoss = 40.0,
        reminderIntervalDays = 3,
        targetTemp = 12.0,
        targetHumidity = 72.0
    )
)