package ru.fermocontrol.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/** Internal app storage. No network or storage permissions are needed. */
class ProductStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "fermocontrol-products-v1.json"))

    fun load(): List<Product> {
        val input = try { file.openRead() } catch (error: FileNotFoundException) {
            if (File(file.baseFile.path + ".bak").exists() || file.baseFile.exists()) throw error
            return emptyList()
        }
        val root = input.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        require(root.getInt("schemaVersion") == 1) { "Неподдерживаемая версия данных" }
        val array = root.getJSONArray("products")
        return List(array.length()) { index ->
            val p = array.getJSONObject(index)
            val records = p.getJSONArray("weighings")
            val spices = p.getJSONArray("spices")
            Product(
                id = p.getString("id"), number = p.getInt("number"),
                name = p.getString("name"), basis = p.getString("basis"),
                spices = List(spices.length()) { spices.getString(it) },
                casing = p.getString("casing"), initialWeight = p.getDouble("initialWeight"),
                targetLoss = p.getDouble("targetLoss"), startedAt = p.getLong("startedAt"),
                archivedAt = if (p.isNull("archivedAt")) null else p.getLong("archivedAt"),
                weighings = List(records.length()) { r ->
                    val w = records.getJSONObject(r)
                    val o = w.getJSONObject("observations")
                    val observations = mutableMapOf<String, String>()
                    val keys = o.keys()
                    while (keys.hasNext()) { val key = keys.next(); observations[key] = o.getString(key) }
                    Weighing(w.getDouble("weight"), w.getLong("at"), observations, w.optString("note", ""))
                }
            )
        }.also(::validateProducts)
    }

    fun save(products: List<Product>) {
        validateProducts(products)
        val array = JSONArray()
        products.forEach { p ->
            val weights = JSONArray()
            p.weighings.forEach { w ->
                weights.put(JSONObject().put("weight", w.weight).put("at", w.at)
                    .put("observations", JSONObject(w.observations)).put("note", w.note))
            }
            array.put(JSONObject().put("id", p.id).put("number", p.number)
                .put("name", p.name).put("basis", p.basis).put("spices", JSONArray(p.spices))
                .put("casing", p.casing).put("initialWeight", p.initialWeight)
                .put("targetLoss", p.targetLoss).put("startedAt", p.startedAt)
                .put("archivedAt", p.archivedAt ?: JSONObject.NULL).put("weighings", weights))
        }
        val bytes = JSONObject().put("schemaVersion", 1).put("products", array)
            .toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
