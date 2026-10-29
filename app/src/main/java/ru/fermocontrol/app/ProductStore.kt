package ru.fermocontrol.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/**
 * Хранилище продуктов на базе SQLite (JournalDatabase) с автоматической
 * однократной миграцией из прежнего файла fermocontrol-products-v1.json.
 */
class ProductStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = JournalDatabase(appContext)
    private val legacyFile = AtomicFile(File(appContext.filesDir, "fermocontrol-products-v1.json"))
    private val migratedBackupFile = File(appContext.filesDir, "fermocontrol-products-v1.migrated.bak")
    private val migrationMarker = File(appContext.filesDir, "fermocontrol-sqlite-v1.migrated")
    private val beforeRestore = AtomicFile(File(appContext.filesDir, "fermocontrol-before-restore.json"))

    @Synchronized
    fun load(): List<Product> {
        migrateLegacyJsonIfNeeded()
        return database.readAll()
    }

    @Synchronized
    fun save(products: List<Product>) {
        // Проверяем сериализуемость и ограничения перед записью в SQLite
        BackupCodec.encode(products)
        database.replaceAll(products)
        markMigrationDone()
    }

    /** Сохраняет текущее состояние в локальную страховочную копию перед полной заменой базы. */
    @Synchronized
    fun restore(products: List<Product>) {
        BackupCodec.encode(products)
        val previous = currentSnapshotBytes()
        atomicWrite(beforeRestore, previous)
        database.replaceAll(products)
        markMigrationDone()
    }

    @Synchronized
    fun recoveryBytes(): ByteArray = readExisting(beforeRestore)
        ?: throw IllegalStateException("Локальной копии перед восстановлением пока нет")

    @Synchronized
    fun hasRecoveryCopy(): Boolean = beforeRestore.baseFile.exists() ||
        File(beforeRestore.baseFile.path + ".bak").exists()

    private fun migrateLegacyJsonIfNeeded() {
        if (migrationMarker.exists()) return

        if (!database.isEmpty()) {
            markMigrationDone()
            return
        }

        val rawBytes = readExisting(legacyFile)
        if (rawBytes == null) {
            markMigrationDone()
            return
        }

        val legacyProducts = BackupCodec.decode(rawBytes)
        database.replaceAll(legacyProducts)
        if (!migratedBackupFile.exists()) {
            runCatching { migratedBackupFile.writeBytes(rawBytes) }
        }
        markMigrationDone()
    }

    private fun markMigrationDone() {
        if (!migrationMarker.exists()) {
            runCatching { migrationMarker.writeText("migrated") }
        }
    }

    private fun currentSnapshotBytes(): ByteArray {
        return try {
            val currentProducts = database.readAll()
            if (currentProducts.isNotEmpty() || migrationMarker.exists()) {
                BackupCodec.encode(currentProducts)
            } else {
                readExisting(legacyFile) ?: BackupCodec.encode(emptyList())
            }
        } catch (_: Exception) {
            readExisting(legacyFile) ?: BackupCodec.encode(emptyList())
        }
    }

    private fun readExisting(source: AtomicFile): ByteArray? {
        val input = try {
            source.openRead()
        } catch (error: FileNotFoundException) {
            if (source.baseFile.exists() || File(source.baseFile.path + ".bak").exists()) throw error
            return null
        }
        return input.use(BackupCodec::read)
    }

    private fun atomicWrite(target: AtomicFile, bytes: ByteArray) {
        val output = target.startWrite()
        try {
            output.write(bytes)
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    }
}