package ru.fermocontrol.data

import org.json.JSONArray
import org.json.JSONObject
import ru.fermocontrol.model.Batch
import ru.fermocontrol.model.WeightLog
import ru.fermocontrol.model.MAX_SPICE_GRAMS_PER_KG
import ru.fermocontrol.model.SpiceItem

object JournalCodec {

    fun encode(batches: List<Batch>): String {
        validate(batches)

        val array = JSONArray()
        batches.forEach { array.put(encodeBatch(it)) }

        return JSONObject().apply {
            put("app", "ФермоКонтроль")
            put("format", "fermocontrol")
            put("formatVersion", 1)
            put("version", "1.0")
            put("createdAt", System.currentTimeMillis().toString())
            put("batches", array)
        }.toString(2)
    }

    fun decode(json: String): List<Batch> {
        val root = JSONObject(json)

        if (root.has("format")) {
            require(root.getString("format") == "fermocontrol") {
                "Неизвестный формат журнала"
            }
        }

        if (root.has("formatVersion")) {
            require(root.getInt("formatVersion") == 1) {
                "Эта версия формата журнала не поддерживается"
            }
        }

        // Отсутствующий или поврежденный массив — ошибка,
        // а не повод вернуть пустой журнал.
        val array = root.getJSONArray("batches")
        val batches = List(array.length()) { index ->
            decodeBatch(array.getJSONObject(index))
        }

        validate(batches)
        return batches
    }

    private fun encodeBatch(batch: Batch): JSONObject {
        val logs = JSONArray()
        batch.logs.forEach { logs.put(encodeLog(it)) }

        return JSONObject().apply {
            put("id", batch.id)
            put("number", batch.number)
            put("name", batch.name)
            put("category", batch.category)
            put("rawMaterial", batch.rawMaterial)
            put("spices", batch.spices)
			            if (batch.spiceItems.isNotEmpty()) {
                val items = JSONArray()
                batch.spiceItems.forEach {
                    items.put(JSONObject().apply {
                        put("name", it.name)
                        put("gramsPerKg", it.gramsPerKg)
                    })
                }
                put("spiceItems", items)
            }
            put("casing", batch.casing)
            put("startWeightGrams", batch.startWeightGrams)
            put("targetLossPercent", batch.targetLossPercent)
            put("createdAtEpochMs", batch.createdAtEpochMs)
            put("archived", batch.archived)
            put("note", batch.note)
            batch.temperatureC?.let { put("temperatureC", it) }
            batch.humidityPercent?.let { put("humidityPercent", it) }
            batch.photoFileName?.let { put("photoFileName", it) }
			batch.productTypeId?.let { put("productTypeId", it) }
            put("logs", logs)
        }
    }

    private fun encodeLog(log: WeightLog): JSONObject =
        JSONObject().apply {
            put("atEpochMs", log.atEpochMs)
            put("weightGrams", log.weightGrams)
            put("smellNote", log.smellNote)
            put("surfaceNote", log.surfaceNote)
            put("needsCheck", log.needsCheck)
            log.photoFileName?.let { put("photoFileName", it) }
        }

    private fun decodeBatch(obj: JSONObject): Batch {
        val logsArray = if (obj.isNull("logs")) {
            JSONArray()
        } else {
            obj.getJSONArray("logs")
        }

        return Batch(
            id = obj.getLong("id"),
            number = obj.getInt("number"),
            name = obj.getString("name"),
            category = obj.textOrEmpty("category"),
            rawMaterial = obj.textOrEmpty("rawMaterial"),
            spices = obj.textOrEmpty("spices"),
			spiceItems = decodeSpiceItems(obj),
            casing = obj.textOrEmpty("casing"),
			productTypeId = obj.nullableText("productTypeId") ?: obj.nullableText("catalogId"),
            startWeightGrams = obj.getDouble("startWeightGrams"),
            targetLossPercent = obj.getDouble("targetLossPercent"),
            temperatureC = obj.nullableDouble("temperatureC"),
            humidityPercent = obj.nullableDouble("humidityPercent"),
            note = obj.textOrEmpty("note"),
            photoFileName = obj.nullableText("photoFileName"),
            createdAtEpochMs = obj.getLong("createdAtEpochMs"),
            archived = if (obj.isNull("archived")) {
                false
            } else {
                obj.getBoolean("archived")
            },
            logs = List(logsArray.length()) { index ->
                decodeLog(logsArray.getJSONObject(index))
            },
        )
    }

    private fun decodeLog(obj: JSONObject): WeightLog =
        WeightLog(
            atEpochMs = obj.getLong("atEpochMs"),
            weightGrams = obj.getDouble("weightGrams"),
            smellNote = obj.textOrEmpty("smellNote"),
            surfaceNote = obj.textOrEmpty("surfaceNote"),
            needsCheck = if (obj.isNull("needsCheck")) {
                false
            } else {
                obj.getBoolean("needsCheck")
            },
            photoFileName = obj.nullableText("photoFileName"),
        )
    private fun decodeSpiceItems(obj: JSONObject): List<SpiceItem> {
        if (obj.isNull("spiceItems")) return emptyList()
        val array = obj.getJSONArray("spiceItems")
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            SpiceItem(
                name = item.getString("name"),
                gramsPerKg = item.getDouble("gramsPerKg"),
            )
        }
    }
    private fun JSONObject.textOrEmpty(key: String): String =
        if (isNull(key)) "" else getString(key)

    private fun JSONObject.nullableText(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private fun validate(batches: List<Batch>) {
        require(batches.map { it.id }.toSet().size == batches.size) {
            "В журнале повторяются идентификаторы партий"
        }

        batches.forEach { batch ->
            require(batch.id > 0L && batch.number > 0) {
                "Некорректный идентификатор или номер партии"
            }
            require(batch.name.isNotBlank()) {
                "У партии отсутствует название"
            }
            require(
                batch.startWeightGrams.isFinite() &&
                    batch.startWeightGrams > 0.0
            ) {
                "Некорректный начальный вес партии №${batch.number}"
            }
            require(
                batch.targetLossPercent.isFinite() &&
                    batch.targetLossPercent > 0.0 &&
                    batch.targetLossPercent < 100.0
            ) {
                "Целевая усушка должна быть больше 0 и меньше 100"
            }
            require(batch.createdAtEpochMs > 0L) {
                "Некорректная дата закладки"
            }
            batch.temperatureC?.let {
                require(it.isFinite()) {
                    "Некорректная температура"
                }
            }
            batch.humidityPercent?.let {
                require(it.isFinite() && it in 0.0..100.0) {
                    "Влажность должна быть от 0 до 100"
                }
            }
            require(
                batch.spiceItems.all {
                    it.name.isNotBlank() &&
                        it.gramsPerKg.isFinite() &&
                        it.gramsPerKg > 0.0 &&
                        it.gramsPerKg <= MAX_SPICE_GRAMS_PER_KG
                }
            ) {
                "Некорректные специи партии №${batch.number}"
            }
            require(
                batch.spiceItems.map { it.name.trim().lowercase() }.toSet().size ==
                    batch.spiceItems.size
            ) {
                "В партии №${batch.number} повторяются специи"
            }
			require(batch.logs.all {
                it.atEpochMs > 0L &&
                    it.weightGrams.isFinite() &&
                    it.weightGrams > 0.0
            }) {
                "Некорректная запись взвешивания партии №${batch.number}"
            }
        }
    }
}