package ru.fermocontrol.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Реляционное хранилище журнала ферментации в SQLite.
 * Все операции записи выполняются в транзакциях с проверкой целостности.
 */
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
                archived_at INTEGER NULL
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
                // Пошаговые миграции для будущих версий схемы (1 -> 2 и т.д.)
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
            SELECT product_id, weight, recorded_at, observations_json, note
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
                weighingsByProduct.getOrPut(productId) { mutableListOf() }.add(
                    Weighing(
                        weight = weight,
                        at = at,
                        observations = observations,
                        note = note
                    )
                )
            }
        }

        val products = mutableListOf<Product>()
        db.rawQuery(
            """
            SELECT id, number, name, basis, spices_json, casing,
                   initial_weight, target_loss, started_at, archived_at
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
                val spicesArray = JSONArray(cursor.getString(4))
                val spices = List(spicesArray.length()) { i -> spicesArray.getString(i) }
                val casing = cursor.getString(5)
                val initialWeight = cursor.getDouble(6)
                val targetLoss = cursor.getDouble(7)
                val startedAt = cursor.getLong(8)
                val archivedAt = if (cursor.isNull(9)) null else cursor.getLong(9)

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
                        weighings = weighingsByProduct[id] ?: emptyList()
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
                }
                val productRowId = db.insertOrThrow("products", null, productValues)
                check(productRowId != -1L) { "Не удалось сохранить продукт ${product.id}" }

                product.weighings.forEachIndexed { weighingIndex, weighing ->
                    val weighingValues = ContentValues().apply {
                        put("product_id", product.id)
                        put("position", weighingIndex)
                        put("weight", weighing.weight)
                        put("recorded_at", weighing.at)
                        put("observations_json", JSONObject(weighing.observations).toString())
                        put("note", weighing.note)
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
        const val SCHEMA_VERSION = 1
    }
}