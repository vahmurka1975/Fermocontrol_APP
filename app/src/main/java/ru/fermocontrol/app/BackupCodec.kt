package ru.fermocontrol.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_PRODUCTS = 10000
    private const val MAX_WEIGHINGS = 200000

    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= MAX_BYTES) { "Файл больше 20 МБ" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<Product> {
        require(bytes.size <= MAX_BYTES) { "Файл больше 20 МБ" }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(text.trim().endsWith("}")) { "Ожидается JSON-файл резервной копии" }
        val tokener = org.json.JSONTokener(text)
        val root = tokener.nextValue() as? JSONObject
            ?: throw IllegalArgumentException("Ожидается JSON-объект")
        require(tokener.nextClean() == '\u0000') { "Лишние данные после JSON" }
        val version = root.whole("schemaVersion")
        require(version == 1L) { "Неподдерживаемая версия данных: $version" }
        val array = root.getJSONArray("products")
        require(array.length() <= MAX_PRODUCTS) { "Слишком много продуктов" }
        var total = 0L
        return List(array.length()) { index ->
            val p = array.getJSONObject(index)
            val records = p.getJSONArray("weighings")
            total += records.length()
            require(total <= MAX_WEIGHINGS) { "Слишком много взвешиваний" }
            val spices = p.getJSONArray("spices")
            val number = p.whole("number")
            require(number in 1..Int.MAX_VALUE.toLong()) { "Неверный номер продукта" }
            val reminderDays = if (p.has("reminderIntervalDays") && !p.isNull("reminderIntervalDays")) {
                p.whole("reminderIntervalDays").toInt()
            } else 3
            val targetTemp = p.optionalDecimal("targetTemp")
            val targetHumidity = p.optionalDecimal("targetHumidity")

            Product(
                id = p.text("id"),
                number = number.toInt(),
                name = p.text("name"),
                basis = p.text("basis"),
                spices = List(spices.length()) {
                    spices.get(it) as? String ?: throw IllegalArgumentException("Неверная специя")
                },
                casing = p.text("casing"),
                initialWeight = p.decimal("initialWeight"),
                targetLoss = p.decimal("targetLoss"),
                startedAt = p.timestamp("startedAt"),
                archivedAt = if (!p.has("archivedAt") || p.isNull("archivedAt")) null
                    else p.timestamp("archivedAt"),
                weighings = List(records.length()) { r ->
                    val w = records.getJSONObject(r)
                    val o = w.getJSONObject("observations")
                    val observations = mutableMapOf<String, String>()
                    val keys = o.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        observations[key] = o.text(key)
                    }
                    Weighing(
                        weight = w.decimal("weight"),
                        at = w.timestamp("at"),
                        observations = observations,
                        note = if (w.has("note") && !w.isNull("note")) w.text("note") else "",
                        temperature = w.optionalDecimal("temperature"),
                        humidity = w.optionalDecimal("humidity")
                    )
                },
                reminderIntervalDays = reminderDays,
                targetTemp = targetTemp,
                targetHumidity = targetHumidity
            )
        }.also(::validateProducts)
    }

    fun encode(products: List<Product>): ByteArray {
        validateProducts(products)
        require(products.size <= MAX_PRODUCTS) { "Слишком много продуктов" }
        require(products.sumOf { it.weighings.size.toLong() } <= MAX_WEIGHINGS) {
            "Слишком много взвешиваний"
        }
        val array = JSONArray()
        products.forEach { p ->
            val weights = JSONArray()
            p.weighings.forEach { w ->
                val wObj = JSONObject()
                    .put("weight", w.weight)
                    .put("at", w.at)
                    .put("observations", JSONObject(w.observations))
                    .put("note", w.note)
                if (w.temperature != null) wObj.put("temperature", w.temperature)
                if (w.humidity != null) wObj.put("humidity", w.humidity)
                weights.put(wObj)
            }
            val pObj = JSONObject()
                .put("id", p.id)
                .put("number", p.number)
                .put("name", p.name)
                .put("basis", p.basis)
                .put("spices", JSONArray(p.spices))
                .put("casing", p.casing)
                .put("initialWeight", p.initialWeight)
                .put("targetLoss", p.targetLoss)
                .put("startedAt", p.startedAt)
                .put("archivedAt", p.archivedAt ?: JSONObject.NULL)
                .put("reminderIntervalDays", p.reminderIntervalDays)
                .put("weighings", weights)
            if (p.targetTemp != null) pObj.put("targetTemp", p.targetTemp)
            if (p.targetHumidity != null) pObj.put("targetHumidity", p.targetHumidity)
            array.put(pObj)
        }
        val bytes = JSONObject()
            .put("schemaVersion", 1)
            .put("products", array)
            .toString()
            .toByteArray(Charsets.UTF_8)
        decode(bytes)
        return bytes
    }

    private fun JSONObject.text(key: String): String = get(key) as? String
        ?: throw IllegalArgumentException("Неверное текстовое поле: $key")

    private fun JSONObject.decimal(key: String): Double {
        val n = get(key) as? Number ?: throw IllegalArgumentException("Неверное число: $key")
        return n.toDouble().also { require(it.isFinite()) { "Неверное число: $key" } }
    }

    private fun JSONObject.optionalDecimal(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return decimal(key)
    }

    private fun JSONObject.whole(key: String): Long {
        val n = get(key)
        require(n is Int || n is Long) { "Ожидается целое число: $key" }
        return (n as Number).toLong()
    }

    private fun JSONObject.timestamp(key: String): Long = whole(key).also {
        require(it in 1L..253402300799999L) { "Неверная дата: $key" }
    }
}