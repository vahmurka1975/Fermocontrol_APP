package ru.fermocontrol.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class JournalDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, SCHEMA_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id TEXT PRIMARY KEY NOT NULL,
                position INTEGER NOT NULL,
                number INTEGER NOT NULL,
                name TEXT NOT NULL,
                basis TEXT NOT NULL,
                spices_json TEXT NOT NULL,
                casing TEXT NOT NULL,
                initial_weight REAL NOT NULL,
                target_loss REAL NOT NULL,
                started_at INTEGER NOT NULL,
                archived_at INTEGER NULL,
                reminder_days INTEGER NOT NULL DEFAULT 3,
                target_temp REAL NULL,
                target_humidity REAL NULL,
                photo_path TEXT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_products_archived_number ON products(archived_at, number)")

        db.execSQL(
            """
            CREATE TABLE weighings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                weight REAL NOT NULL,
                recorded_at INTEGER NOT NULL,
                observations_json TEXT NOT NULL,
                note TEXT NOT NULL,
                temperature REAL NULL,
                humidity REAL NULL,
                photo_path TEXT NULL,
                FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_weighings_product_pos ON weighings(product_id, position)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        while (version < newVersion) {
            when (version) {
                1 -> {
                    db.execSQL("ALTER TABLE products ADD COLUMN reminder_days INTEGER NOT NULL DEFAULT 3")
                    db.execSQL("ALTER TABLE products ADD COLUMN target_temp REAL NULL")
                    db.execSQL("ALTER TABLE products ADD COLUMN target_humidity REAL NULL")
                    db.execSQL("ALTER TABLE weighings ADD COLUMN temperature REAL NULL")
                    db.execSQL("ALTER TABLE weighings ADD COLUMN humidity REAL NULL")
                }
                2 -> {
                    db.execSQL("ALTER TABLE products ADD COLUMN photo_path TEXT NULL")
                    db.execSQL("ALTER TABLE weighings ADD COLUMN photo_path TEXT NULL")
                }
                else -> error("Неизвестная миграция схемы БД с версии $version до $newVersion")
            }
            version++
        }
    }

    fun isEmpty(): Boolean {
        val count = readableDatabase
            .compileStatement("SELECT COUNT(*) FROM products")
            .simpleQueryForLong()
        return count == 0L
    }

    fun readAll(): List<Product> {
        val db = readableDatabase
        val weighingsByProduct = LinkedHashMap<String, MutableList<Weighing>>()

        db.rawQuery(
            """
            SELECT product_id, weight, recorded_at, observations_json, note, temperature, humidity, photo_path
            FROM weighings
            ORDER BY position ASC, id ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val productId = cursor.getString(0)
                val weight = cursor.getDouble(1)
                val at = cursor.getLong(2)
                val obsObj = JSONObject(cursor.getString(3))
                val observations = LinkedHashMap<String, String>()
                val keys = obsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    observations[key] = obsObj.optString(key, "")
                }
                val note = cursor.getString(4) ?: ""
                val temp = if (cursor.isNull(5)) null else cursor.getDouble(5)
                val hum = if (cursor.isNull(6)) null else cursor.getDouble(6)
                val photoPath = if (cursor.isNull(7)) null else cursor.getString(7)?.takeIf { it.isNotBlank() }
                weighingsByProduct.getOrPut(productId) { mutableListOf() }.add(
                    Weighing(
                        weight = weight,
                        at = at,
                        observations = observations,
                        note = note,
                        temperature = temp,
                        humidity = hum,
                        photoPath = photoPath
                    )
                )
            }
        }

        val products = mutableListOf<Product>()
        db.rawQuery(
            """
            SELECT id, number, name, basis, spices_json, casing,
                   initial_weight, target_loss, started_at, archived_at,
                   reminder_days, target_temp, target_humidity, photo_path
            FROM products
            ORDER BY position ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val number = cursor.getInt(1)
                val name = cursor.getString(2)
                val basis = cursor.getString(3)
                val spicesArr = JSONArray(cursor.getString(4))
                val spices = List(spicesArr.length()) { idx -> spicesArr.getString(idx) }
                val casing = cursor.getString(5)
                val initialWeight = cursor.getDouble(6)
                val targetLoss = cursor.getDouble(7)
                val startedAt = cursor.getLong(8)
                val archivedAt = if (cursor.isNull(9)) null else cursor.getLong(9)
                val reminderDays = if (cursor.isNull(10)) 3 else cursor.getInt(10).coerceIn(1, 60)
                val targetTemp = if (cursor.isNull(11)) null else cursor.getDouble(11)
                val targetHumidity = if (cursor.isNull(12)) null else cursor.getDouble(12)
                val photoPath = if (cursor.isNull(13)) null else cursor.getString(13)?.takeIf { it.isNotBlank() }

                products.add(
                    Product(
                        id = id,
                        number = number,
                        name = name,
                        basis = basis,
                        spices = spices,
                        casing = casing,
                        initialWeight = initialWeight,
                        targetLoss = targetLoss,
                        startedAt = startedAt,
                        archivedAt = archivedAt,
                        weighings = weighingsByProduct[id]?.toList().orEmpty(),
                        reminderIntervalDays = reminderDays,
                        targetTemp = targetTemp,
                        targetHumidity = targetHumidity,
                        photoPath = photoPath
                    )
                )
            }
        }

        validateProducts(products)
        return products
    }

    fun replaceAll(products: List<Product>) {
        validateProducts(products)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("weighings", null, null)
            db.delete("products", null, null)

            products.forEachIndexed { productIndex, product ->
                val productValues = ContentValues().apply {
                    put("id", product.id)
                    put("position", productIndex)
                    put("number", product.number)
                    put("name", product.name)
                    put("basis", product.basis)
                    put("spices_json", JSONArray(product.spices).toString())
                    put("casing", product.casing)
                    put("initial_weight", product.initialWeight)
                    put("target_loss", product.targetLoss)
                    put("started_at", product.startedAt)
                    if (product.archivedAt != null) {
                        put("archived_at", product.archivedAt)
                    } else {
                        putNull("archived_at")
                    }
                    put("reminder_days", product.reminderIntervalDays)
                    if (product.targetTemp != null) put("target_temp", product.targetTemp) else putNull("target_temp")
                    if (product.targetHumidity != null) put("target_humidity", product.targetHumidity) else putNull("target_humidity")
                    if (!product.photoPath.isNullOrBlank()) put("photo_path", product.photoPath) else putNull("photo_path")
                }
                val rowId = db.insertOrThrow("products", null, productValues)
                check(rowId != -1L) { "Не удалось сохранить продукт ${product.id}" }

                product.weighings.forEachIndexed { weighingIndex, weighing ->
                    val weighingValues = ContentValues().apply {
                        put("product_id", product.id)
                        put("position", weighingIndex)
                        put("weight", weighing.weight)
                        put("recorded_at", weighing.at)
                        put("observations_json", JSONObject(weighing.observations).toString())
                        put("note", weighing.note)
                        if (weighing.temperature != null) put("temperature", weighing.temperature) else putNull("temperature")
                        if (weighing.humidity != null) put("humidity", weighing.humidity) else putNull("humidity")
                        if (!weighing.photoPath.isNullOrBlank()) put("photo_path", weighing.photoPath) else putNull("photo_path")
                    }
                    val weighingRowId = db.insertOrThrow("weighings", null, weighingValues)
                    check(weighingRowId != -1L) { "Не удалось сохранить взвешивание продукта ${product.id}" }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val DB_NAME = "fermocontrol-journal.db"
        const val SCHEMA_VERSION = 3
    }
}