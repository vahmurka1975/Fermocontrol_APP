package ru.fermocontrol.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.fermocontrol.model.Batch

data class SnapshotInfo(
    val file: File,
    val time: Long,
    val reason: String,     // weigh, edit, delete, prerestore
    val batchCount: Int?,   // null, если снимок не читается
)

class BatchRepository(context: Context) {

    private val journalFile = File(
        context.applicationContext.filesDir,
        "fermocontrol_journal.json",
    )

    private val atomicFile = AtomicFile(journalFile)

    val autoBackup = AutoBackup(context)

/** Вызывается после успешной записи журнала, уже вне блокировки. */
    @Volatile
    var onSaved: (() -> Unit)? = null
	
    suspend fun load(): List<Batch> = withContext(Dispatchers.IO) {
        access.withLock { readLocked() }
    }

    /** reason: weigh, edit или delete. Попадает в имя снимка. */
    suspend fun save(batches: List<Batch>, reason: String = "edit"): Unit =
        withContext(Dispatchers.IO) {
            access.withLock {
                val json = JournalCodec.encode(batches)
                writeLocked(json)

                // Снимок делается после успешной записи и не влияет на её результат.
                if (batches.isNotEmpty()) {
                    runCatching { autoBackup.snapshot(json, reason) }
                }
            }
            notifySaved()
        }

    suspend fun snapshots(): List<SnapshotInfo> = withContext(Dispatchers.IO) {
        access.withLock {
            val format = SimpleDateFormat(AutoBackup.STAMP, Locale.US)
            autoBackup.list().map { file ->
                val base = file.name.removeSuffix(AutoBackup.SUFFIX)
                val time = runCatching { format.parse(base.substring(0, 15))?.time }
                    .getOrNull() ?: file.lastModified()
                val count = runCatching {
                    JournalCodec.decode(autoBackup.read(file)).size
                }.getOrNull()
                SnapshotInfo(file, time, base.substring(16), count)
            }
        }
    }

    /**
     * Восстанавливает журнал из снимка.
     * Сначала снимок читается и проверяется, потом делается копия текущего
     * состояния (prerestore), потом вызывается prepare, и только после этого
     * журнал перезаписывается. Если что-то из этого упало, журнал не тронут.
     * В prepare верните фото из корзины (PhotoStore.undelete).
     */
    suspend fun restore(
        snapshot: File,
        prepare: (List<Batch>) -> Unit = {},
    ): List<Batch> = withContext(Dispatchers.IO) {
        val restored = access.withLock {
            val json = autoBackup.read(snapshot)
            val decoded = JournalCodec.decode(json)

            val current = readLocked()
            if (current.isNotEmpty()) {
                autoBackup.snapshot(JournalCodec.encode(current), "prerestore", force = true)
            }

            prepare(decoded)
            writeLocked(JournalCodec.encode(decoded))
            decoded
        }
        notifySaved()
        restored
    }

    private fun notifySaved() {
        runCatching { onSaved?.invoke() }
    }

    private fun readLocked(): List<Batch> {
        val backupFile = File("${journalFile.path}.bak")

        if (!journalFile.exists() && !backupFile.exists()) return emptyList()

        val json = atomicFile.openRead()
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        return JournalCodec.decode(json)
    }

    private fun writeLocked(json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private companion object {
        val access = Mutex()
    }
}