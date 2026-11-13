package ru.fermocontrol.model

/** Список специй для паспорта партии. Править здесь. */
object SpiceCatalog {
    const val NITRITE = "Нитритная соль"
    const val MAX_GRAMS_PER_KG = 100.0
        val items = listOf(
        "Поваренная соль",
        "Чёрный перец",
        "Белый перец",
        "Чеснок сушёный",
        "Паприка сладкая",
        "Паприка копчёная",
        "Мускатный орех",
        "Кориандр",
        "Тмин",
        "Зира",
        "Пажитник",
        "Розмарин",
        "Тимьян",
        "Майоран",
        "Можжевельник",
        "Гвоздика",
        "Душистый перец",
        "Фенхель",
        "Орегано",
    )
}

data class SpiceRow(val name: String, val grams: String)

/** legacy: старая запись свободным текстом, которую не удалось разобрать. */
data class SpiceRecipe(val nitrite: String, val rows: List<SpiceRow>, val legacy: String)

/**
 * В Batch.spices по-прежнему лежит строка, поэтому JournalCodec, MainActivity и BatchDetailSheet не меняются.
 * Формат: «Нитритная соль 28 г/кг; Мускатный орех 0,5 г/кг».
 */
object SpiceCodec {
    private val part = Regex("""^(.+?)\s+(\d+(?:[.,]\d+)?)\s*г/кг$""")

    fun parseGrams(s: String): Double? =
        s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun gramsError(s: String): String? {
        if (s.isBlank()) return null
        val v = parseGrams(s)
        return when {
            v == null || v <= 0.0 -> "Введите число больше 0"
            v > SpiceCatalog.MAX_GRAMS_PER_KG -> "Не больше 100 г на 1 кг"
            else -> null
        }
    }

    fun rowError(r: SpiceRow): String? = when {
        r.name.isBlank() -> if (r.grams.isBlank()) "Выберите специю из списка" else "Выберите специю"
        r.grams.isBlank() -> "Укажите граммы на 1 кг"
        else -> gramsError(r.grams)
    }

    fun isValid(nitrite: String, rows: List<SpiceRow>): Boolean =
        gramsError(nitrite) == null && rows.all { rowError(it) == null }

    fun decode(text: String): SpiceRecipe {
        if (text.isBlank()) return SpiceRecipe("", emptyList(), "")
        var nitrite = ""
        val rows = mutableListOf<SpiceRow>()
        for (p in text.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = part.matchEntire(p) ?: return SpiceRecipe("", emptyList(), text.trim())
            val name = m.groupValues[1].trim()
            val g = m.groupValues[2].replace('.', ',')
            if (name == SpiceCatalog.NITRITE) nitrite = g else rows += SpiceRow(name, g)
        }
        return SpiceRecipe(nitrite, rows, "")
    }

    fun encode(nitrite: String, rows: List<SpiceRow>): String = buildList {
        if (nitrite.isNotBlank()) add("${SpiceCatalog.NITRITE} ${nitrite.trim()} г/кг")
        rows.filter { it.name.isNotBlank() && it.grams.isNotBlank() }
            .forEach { add("${it.name} ${it.grams.trim()} г/кг") }
    }.joinToString("; ")
}
