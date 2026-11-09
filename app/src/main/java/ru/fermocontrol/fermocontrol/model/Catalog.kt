package ru.fermocontrol.fermocontrol.model

/** Позиция каталога. Значения служат стартовыми, пользователь может их изменить. */
data class CatalogItem(
    val id: String,
    val name: String,
    val category: String,
    val rawMaterial: String,
    val targetLossPercent: Double,
    val casing: String = "Без оболочки",
)

object Catalog {

    val categories = listOf("Сыровяление", "Ферментация овощей", "Сыроделие", "Другое")
    val rawMaterials = listOf("Говядина", "Свинина", "Птица", "Рыба", "Сыр", "Овощи")
    val casings = listOf("Айцел Премиум", "Коллагеновая", "Натуральная", "Чудо-рукав", "Без оболочки")

    // Идентификаторы сохраняются в журнале, поэтому их нельзя менять.
    val items: List<CatalogItem> = listOf(
        CatalogItem("bresaola", "Брезаола", "Сыровяление", "Говядина", 33.0),
        CatalogItem("lonza", "Лонцо", "Сыровяление", "Свинина", 30.0),
        CatalogItem("coppa", "Капикола", "Сыровяление", "Свинина", 32.0),
        CatalogItem("prosciutto", "Прошутто", "Сыровяление", "Свинина", 30.0),
        CatalogItem("guanciale", "Гуанчале", "Сыровяление", "Свинина", 30.0),
        CatalogItem("pancetta", "Панчетта", "Сыровяление", "Свинина", 30.0),
        CatalogItem("salami", "Салями", "Сыровяление", "Свинина", 32.0, "Натуральная"),
        CatalogItem("poultry_balyk", "Балык из птицы", "Сыровяление", "Птица", 30.0),
        CatalogItem("duck_breast", "Утиная грудка", "Сыровяление", "Птица", 30.0),
        CatalogItem("cured_fish", "Вяленая рыба", "Сыровяление", "Рыба", 35.0),
        CatalogItem("semi_hard_cheese", "Полутвёрдый сыр", "Сыроделие", "Сыр", 12.0),
        CatalogItem("hard_cheese", "Твёрдый сыр", "Сыроделие", "Сыр", 20.0),
    )

    fun byId(id: String?): CatalogItem? =
        if (id.isNullOrEmpty()) null else items.firstOrNull { it.id == id }
}