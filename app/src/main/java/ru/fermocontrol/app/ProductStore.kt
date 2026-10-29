package ru.fermocontrol.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** All writes are atomic. The data file name and schema from step 1 are retained. */
class ProductStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "fermocontrol-products-v1.json"))
    private val beforeRestore = AtomicFile(File(context.filesDir, "fermocontrol-before-restore.json"))

    @Synchronized fun load(): List<Product> = readExisting(file)?.let(BackupCodec::decode) ?: emptyList()

    @Synchronized fun save(products: List<Product>) {
        atomicWrite(file, BackupCodec.encode(products))
    }

    /** Preserve the original bytes even if the current journal is corrupt. */
    @Synchronized fun restore(products: List<Product>) {
        val next = BackupCodec.encode(products)
        val previous = readExisting(file) ?: BackupCodec.encode(emptyList())
        atomicWrite(beforeRestore, previous)
        atomicWrite(file, next)
    }

    @Synchronized fun recoveryBytes(): ByteArray = readExisting(beforeRestore)
        ?: throw IllegalStateException("Локальной копии перед восстановлением пока нет")

    @Synchronized fun hasRecoveryCopy(): Boolean = beforeRestore.baseFile.exists() ||
        File(beforeRestore.baseFile.path + ".bak").exists()

    private fun readExisting(source: AtomicFile): ByteArray? {
        val input = try { source.openRead() } catch (error: FileNotFoundException) {
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
