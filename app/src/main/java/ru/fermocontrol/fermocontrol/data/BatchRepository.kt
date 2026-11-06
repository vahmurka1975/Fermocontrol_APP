package ru.fermocontrol.fermocontrol.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.fermocontrol.fermocontrol.model.Batch

class BatchRepository(context: Context) {

    private val journalFile = File(
        context.applicationContext.filesDir,
        "fermocontrol_journal.json",
    )

    private val atomicFile = AtomicFile(journalFile)

    suspend fun load(): List<Batch> = withContext(Dispatchers.IO) {
        access.withLock {
            val backupFile = File("${journalFile.path}.bak")

            if (!journalFile.exists() && !backupFile.exists()) {
                emptyList()
            } else {
                val json = atomicFile.openRead()
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                JournalCodec.decode(json)
            }
        }
    }

    suspend fun save(batches: List<Batch>): Unit =
        withContext(Dispatchers.IO) {
            access.withLock {
                // Сначала проверяем и кодируем данные.
                // Если это не удалось, файл вообще не открывается на запись.
                val bytes = JournalCodec.encode(batches)
                    .toByteArray(Charsets.UTF_8)

                val output = atomicFile.startWrite()

                try {
                    output.write(bytes)
                    atomicFile.finishWrite(output)
                } catch (error: Exception) {
                    atomicFile.failWrite(output)
                    throw error
                }
            }
        }

    private companion object {
        // Общая блокировка для экземпляров хранилища в одном процессе.
        val access = Mutex()
    }
}