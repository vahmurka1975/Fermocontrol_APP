package ru.fermocontrol.fermocontrol.model

/** Позиция каталога. Значения служат стартовыми, пользователь может их изменить. */
data class CatalogItem(
    val id: String,
    val name: String,
    val category: String,
    /** Сырьё по умолчанию. null, если пользователь выбирает его сам. */
    val rawMaterial: String?,
    val targetLossPercent: Double,
    /** Оболочка по умолчанию. null, если пользователь выбирает её сам. */
    val casing: String? = null,
) {
    /** Имя файла заглушки в assets/catalog/placeholders. */
    val photoFile: String get() = "ph_$id.webp"
}

object Catalog {

    /** Заглушка для партий без позиции каталога. */
    const val DEFAULT_PHOTO_FILE = "ph_default.webp"

    val categories = listOf("Сыровяление", "Ферментация овощей", "Сыроделие", "Другое")
    val rawMaterials = listOf("Говядина", "Свинина", "Птица", "Конина", "Рыба", "Кальмар", "Сыр", "Овощи")
    val casings = listOf("Айцел Премиум", "Коллагеновая", "Натуральная", "Чудо-рукав", "Без оболочки")

    // Идентификаторы сохраняются в журнале, поэтому их нельзя менять.
    // Id, которых больше нет в списке, остаются в старых журналах и ведут на новую позицию.
    private val legacyIds = mapOf(
        "lonza" to "polendvitsa",
        "cured_fish" to "dried_fish",
    )

    val items: List<CatalogItem> = listOf(
        CatalogItem("sausage", "Колбаса сыровяленая", "Сыровяление", "Свинина", 38.0),
        CatalogItem("bresaola", "Брезаола", "Сыровяление", "Говядина", 40.0),
        CatalogItem("polendvitsa", "Полендвица", "Сыровяление", "Свинина", 38.0),
        CatalogItem("coppa", "Коппа (Капоколло)", "Сыровяление", "Свинина", 38.0),
        CatalogItem("prosciutto", "Прошутто", "Сыровяление", "Свинина", 35.0),
        CatalogItem("guanciale", "Гуанчале", "Сыровяление", "Свинина", 30.0),
        CatalogItem("pancetta", "Панчетта", "Сыровяление", "Свинина", 40.0),
        CatalogItem("salami", "Салями", "Сыровяление", "Свинина", 40.0),
        CatalogItem("chorizo", "Чоризо", "Сыровяление", "Свинина", 38.0),
        CatalogItem("fouett", "Фуэт", "Сыровяление", "Свинина", 38.0),
        CatalogItem("sujuk", "Суджук", "Сыровяление", "Говядина", 38.0),
        CatalogItem("mahan", "Махан", "Сыровяление", "Конина", 38.0),
        CatalogItem("kazylik", "Казылык", "Сыровяление", "Конина", 38.0),
        CatalogItem("salchichon", "Сальчичон", "Сыровяление", "Свинина", 38.0),
        CatalogItem("longaniza", "Лонганиза", "Сыровяление", "Свинина", 38.0),
        CatalogItem("spiynata", "Спьяната", "Сыровяление", "Свинина", 38.0),
        CatalogItem("poultry_balyk", "Балык из птицы", "Сыровяление", "Птица", 40.0),
        CatalogItem("duck_breast", "Утиная грудка", "Сыровяление", "Птица", 38.0),
        CatalogItem("dried_fish", "Вяленая рыба", "Сыровяление", "Рыба", 50.0),
        CatalogItem("basturma", "Бастурма", "Сыровяление", "Говядина", 38.0),
        CatalogItem("balyk", "Балык", "Сыровяление", null, 35.0),
        CatalogItem("grudinka", "Грудинка сыровяленая", "Сыровяление", "Свинина", 38.0),
        CatalogItem("vyrezka", "Вырезка сыровяленая", "Сыровяление", null, 38.0),
        CatalogItem("jamon", "Хамон", "Сыровяление", "Свинина", 38.0),
        CatalogItem("karbonad", "Карбонад сыровяленый", "Сыровяление", "Свинина", 38.0),
        CatalogItem("horse_meat", "Конина сыровяленая", "Сыровяление", "Конина", 45.0),
        CatalogItem("dried_squid", "Кальмар сушёно-вяленый", "Сыровяление", "Кальмар", 55.0),
        CatalogItem("semi_hard_cheese", "Полутвёрдый сыр", "Сыроделие", "Сыр", 12.0),
        CatalogItem("hard_cheese", "Твёрдый сыр", "Сыроделие", "Сыр", 20.0),
    )

    fun byId(id: String?): CatalogItem? {
        if (id.isNullOrEmpty()) return null
        val actual = legacyIds[id] ?: id
        return items.firstOrNull { it.id == actual }
    }
}