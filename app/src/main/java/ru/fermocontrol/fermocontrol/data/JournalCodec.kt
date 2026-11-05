package ru.fermocontrol.fermocontrol.data

import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.WeightLog

object JournalCodec {

    fun encode(batches: List<Batch>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": \"ФермоКонтроль\",\n")
        sb.append("  \"format\": \"fermocontrol\",\n")
        sb.append("  \"formatVersion\": 1,\n")
        sb.append("  \"version\": \"1.0\",\n")
        sb.append("  \"createdAt\": \"${System.currentTimeMillis()}\",\n")
        sb.append("  \"batches\": [")
        batches.forEachIndexed { i, b ->
            if (i > 0) sb.append(",")
            sb.append("\n    ").append(encodeBatch(b))
        }
        sb.append("\n  ]\n}")
        return sb.toString()
    }

    private fun encodeBatch(b: Batch): String {
        val items = mutableListOf(
            "\"id\": ${b.id}",
            "\"number\": ${b.number}",
            "\"name\": ${json(b.name)}",
            "\"category\": ${json(b.category)}",
            "\"rawMaterial\": ${json(b.rawMaterial)}",
            "\"spices\": ${json(b.spices)}",
            "\"casing\": ${json(b.casing)}",
            "\"startWeightGrams\": ${b.startWeightGrams}",
            "\"targetLossPercent\": ${b.targetLossPercent}",
            "\"archived\": ${b.archived}",
            "\"createdAtEpochMs\": ${b.createdAtEpochMs}"
        )
        b.temperatureC?.let { items.add("\"temperatureC\": $it") }
        b.humidityPercent?.let { items.add("\"humidityPercent\": $it") }
        items.add("\"note\": ${json(b.note)}")
        b.photoFileName?.let { items.add("\"photoFileName\": ${json(it)}") }

        val logs = b.logs.joinToString(",") { encodeLog(it) }
        items.add("\"logs\": [$logs]")

        return "{" + items.joinToString(", ") + "}"
    }

    private fun encodeLog(l: WeightLog): String {
        val items = mutableListOf(
            "\"atEpochMs\": ${l.atEpochMs}",
            "\"weightGrams\": ${l.weightGrams}",
            "\"smellNote\": ${json(l.smellNote)}",
            "\"surfaceNote\": ${json(l.surfaceNote)}",
            "\"needsCheck\": ${l.needsCheck}"
        )
        l.photoFileName?.let { items.add("\"photoFileName\": ${json(it)}") }
        return "{" + items.joinToString(", ") + "}"
    }

    private fun json(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append("\"").toString()
    }

    // ---------- Декодирование ----------
    fun decode(json: String): List<Batch> {
        val batchesArr = extractArray(json, "batches") ?: return emptyList()
        return batchesArr.map { decodeBatch(it) }
    }

    private fun decodeBatch(json: String): Batch {
        fun get(key: String): String? = extractValue(json, key)

        return Batch(
            id = get("id")?.trim()?.toLongOrNull() ?: 0L,
            number = get("number")?.trim()?.toIntOrNull() ?: 0,
            name = get("name") ?: "",
            category = get("category") ?: "",
            rawMaterial = get("rawMaterial") ?: "",
            spices = get("spices") ?: "",
            casing = get("casing") ?: "",
            startWeightGrams = get("startWeightGrams")?.trim()?.toDoubleOrNull() ?: 0.0,
            targetLossPercent = get("targetLossPercent")?.trim()?.toDoubleOrNull() ?: 30.0,
            temperatureC = get("temperatureC")?.trim()?.toDoubleOrNull(),
            humidityPercent = get("humidityPercent")?.trim()?.toDoubleOrNull(),
            note = get("note") ?: "",
            photoFileName = get("photoFileName"),
            createdAtEpochMs = get("createdAtEpochMs")?.trim()?.toLongOrNull() ?: 0L,
            archived = get("archived")?.trim()?.toBoolean() ?: false,
            logs = extractArray(json, "logs")?.map { decodeLog(it) } ?: emptyList()
        )
    }

    private fun decodeLog(json: String): WeightLog {
        fun get(key: String): String? = extractValue(json, key)
        return WeightLog(
            atEpochMs = get("atEpochMs")?.trim()?.toLongOrNull() ?: 0L,
            weightGrams = get("weightGrams")?.trim()?.toDoubleOrNull() ?: 0.0,
            smellNote = get("smellNote") ?: "",
            surfaceNote = get("surfaceNote") ?: "",
            needsCheck = get("needsCheck")?.trim()?.toBoolean() ?: false,
            photoFileName = get("photoFileName")
        )
    }

    private fun extractValue(json: String, key: String): String? {
        val idx = json.indexOf("\"$key\"")
        if (idx < 0) return null
        val colon = json.indexOf(':', idx)
        if (colon < 0) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        return when (json[i]) {
            '"' -> {
                val sb = StringBuilder()
                i++
                while (i < json.length && json[i] != '"') {
                    if (json[i] == '\\' && i + 1 < json.length) {
                        when (json[i + 1]) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r')
                            't' -> sb.append('\t'); '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            'u' -> {
                                val hex = json.substring(i + 2, i + 6)
                                sb.append(hex.toInt(16).toChar()); i += 4
                            }
                        }
                        i++
                    } else sb.append(json[i])
                    i++
                }
                sb.toString()
            }
            else -> {
                val start = i
                while (i < json.length && json[i] != ',' && json[i] != '}' && json[i] != ']') i++
                json.substring(start, i)
            }
        }
    }

    private fun extractArray(json: String, key: String): List<String>? {
        val idx = json.indexOf("\"$key\"")
        if (idx < 0) return null
        val colon = json.indexOf(':', idx)
        if (colon < 0) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '[') return null

        val sb = StringBuilder()
        var depth = 0
        var inStr = false
        var j = i
        while (j < json.length) {
            val c = json[j]
            if (inStr) {
                if (c == '\\') { j++; sb.append(c).append(json.getOrNull(j) ?: ' '); j++; continue }
                if (c == '"') inStr = false
                sb.append(c); j++; continue
            }
            when (c) {
                '"' -> inStr = true
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { j++; break } }
                else -> {}
            }
            sb.append(c); j++
        }
        if (sb.isEmpty()) return emptyList()
        return splitTopLevel(sb.toString().removeSurrounding("[", "]"))
    }

    private fun splitTopLevel(s: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var inStr = false
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') { cur.append(c).append(s.getOrNull(i + 1) ?: ' '); i += 2; continue }
                if (c == '"') inStr = false
                cur.append(c); i++; continue
            }
            when (c) {
                '"' -> { inStr = true; cur.append(c) }
                '{', '[' -> { depth++; cur.append(c) }
                '}', ']' -> { depth--; cur.append(c) }
                ',' -> if (depth == 0) { result.add(cur.toString()); cur.clear() } else cur.append(c)
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) result.add(cur.toString())
        return result
    }
}