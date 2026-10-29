package ru.fermocontrol.app

import java.time.Instant
import java.util.Locale

object CsvExporter {
    fun encode(products: List<Product>): ByteArray {
        validateProducts(products)
        val output = StringBuilder("\uFEFF")
        fun row(cells: List<String>, numeric: Set<Int> = emptySet()) {
            output.append(cells.mapIndexed { index, cell ->
                val safe = if (index !in numeric && cell.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$cell" else cell
                "\"" + safe.replace("\"", "\"\"") + "\""
            }.joinToString(";")).append("\r\n")
        }
        row(listOf("ID продукта", "Номер", "Название", "Статус", "Основа", "Специи", "Оболочка",
            "Создан UTC", "Извлечён UTC", "Тип записи", "Дата UTC", "Вес г", "Потеря %",
            "Целевая потеря %", "Целевой вес г", "Температура C", "Влажность %", "Наблюдения", "Заметка"))
        products.forEach { p ->
            val common = listOf(p.id, p.number.toString(), p.name,
                if (p.archivedAt == null) "Активный" else "Архив", p.basis,
                p.spices.joinToString(", "), p.casing, instant(p.startedAt),
                p.archivedAt?.let(::instant).orEmpty())
            fun record(type: String, at: Long, weight: Double, temp: Double?, hum: Double?, observations: String, note: String) {
                row(common + listOf(type, instant(at), number(weight),
                    number((p.initialWeight - weight) / p.initialWeight * 100.0),
                    number(p.targetLoss), number(p.targetWeight),
                    temp?.let(::number).orEmpty(), hum?.let(::number).orEmpty(),
                    observations, note), setOf(1, 11, 12, 13, 14, 15, 16))
            }
            record("Начальный вес", p.startedAt, p.initialWeight, p.targetTemp, p.targetHumidity, "", "")
            p.weighings.forEach { w ->
                record("Взвешивание", w.at, w.weight, w.temperature, w.humidity,
                    w.observations.entries.sortedBy { it.key }.joinToString(" | ") { "${it.key}: ${it.value}" },
                    w.note)
            }
        }
        return output.toString().toByteArray(Charsets.UTF_8)
    }

    private fun instant(value: Long) = Instant.ofEpochMilli(value).toString()
    private fun number(value: Double) = String.format(Locale.ROOT, "%.3f", value)
}
