package ru.fermocontrol.app

import java.time.Instant
import java.util.Locale

/** UTF-8 BOM, semicolon-separated, quoted cells, UTC timestamps. */
object CsvExporter {
    fun encode(products: List<Product>): ByteArray {
        validateProducts(products)
        val output = StringBuilder("\uFEFF")
        fun row(cells: List<String>, numeric: Set<Int> = emptySet()) {
            output.append(cells.mapIndexed { index, cell ->
                // Prevent spreadsheet formulas in user-controlled text.
                val safe = if (index !in numeric && cell.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$cell" else cell
                "\"" + safe.replace("\"", "\"\"") + "\""
            }.joinToString(";")).append("\r\n")
        }
        row(listOf("ID продукта", "Номер", "Название", "Статус", "Основа", "Специи", "Оболочка",
            "Создан UTC", "Извлечён UTC", "Тип записи", "Дата UTC", "Вес г", "Потеря %",
            "Целевая потеря %", "Целевой вес г", "Наблюдения", "Заметка"))
        products.forEach { p ->
            val common = listOf(p.id, p.number.toString(), p.name,
                if (p.archivedAt == null) "Активный" else "Архив", p.basis,
                p.spices.joinToString(", "), p.casing, instant(p.startedAt),
                p.archivedAt?.let(::instant).orEmpty())
            fun record(type: String, at: Long, weight: Double, observations: String, note: String) {
                row(common + listOf(type, instant(at), number(weight),
                    number((p.initialWeight - weight) / p.initialWeight * 100.0),
                    number(p.targetLoss), number(p.targetWeight), observations, note), setOf(1, 11, 12, 13, 14))
            }
            record("Начальный вес", p.startedAt, p.initialWeight, "", "")
            p.weighings.forEach { w ->
                record("Взвешивание", w.at, w.weight,
                    w.observations.entries.sortedBy { it.key }.joinToString(" | ") { "${it.key}: ${it.value}" },
                    w.note)
            }
        }
        return output.toString().toByteArray(Charsets.UTF_8)
    }

    private fun instant(value: Long) = Instant.ofEpochMilli(value).toString()
    private fun number(value: Double) = String.format(Locale.ROOT, "%.3f", value)
}
