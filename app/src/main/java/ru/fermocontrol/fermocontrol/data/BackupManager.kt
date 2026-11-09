package ru.fermocontrol.fermocontrol.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fermocontrol.fermocontrol.model.Batch

/** Резервная копия (ZIP), восстановление, отправка и CSV. */
class BackupManager(
    context: Context,
    private val repository: BatchRepository,
    private val photos: PhotoStore,
) {
    private val app = context.applicationContext

    fun backupFileName(): String = "fermocontrol_${stamp()}.zip"
    fun csvFileName(): String = "fermocontrol_${stamp()}.csv"

    /** Пишет ZIP с журналом и фотографиями. Возвращает число партий. */
    suspend fun writeBackup(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val batches = repository.load()
        val json = JournalCodec.encode(batches).toByteArray(Charsets.UTF_8)
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JOURNAL))
            zip.write(json)
            zip.closeEntry()
            referencedPhotos(batches).forEach { name ->
                val file = photos.file(name)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        batches.size
    }

    /**
     * Заменяет журнал содержимым копии. Возвращает число партий.
     * Сначала всё проверяется, и только потом меняются данные.
     */
    suspend fun restore(input: InputStream): Int = withContext(Dispatchers.IO) {
        val staging = File(app.cacheDir, "restore_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var journal: ByteArray? = null
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name == JOURNAL) {
                        val buffer = ByteArrayOutputStream()
                        total += zip.copyLimited(buffer, MAX_JOURNAL)
                        journal = buffer.toByteArray()
                    } else if (entry.name.startsWith("photos/")) {
                        val name = entry.name.removePrefix("photos/")
                        if (!photos.isValidName(name)) continue
                        FileOutputStream(File(staging, name)).use { total += zip.copyLimited(it, MAX_PHOTO) }
                    }
                    if (total > MAX_TOTAL) throw IOException("Копия слишком большая")
                }
            }

            val bytes = journal ?: throw IOException("В файле нет журнала. Это не копия ФермоКонтроля.")
            val batches = try {
                JournalCodec.decode(bytes.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                throw IOException("Журнал в копии повреждён. Текущие данные не тронуты.", error)
            }

            // Страховочная копия текущих данных. Если текущий журнал сам повреждён, пропускаем.
            runCatching {
                val dir = File(app.filesDir, "backups").apply { mkdirs() }
                val tmp = File(dir, "before_restore.tmp")
                FileOutputStream(tmp).use { writeBackup(it) }
                tmp.renameTo(File(dir, "before_restore.zip"))
            }

            // Сначала фото (старые файлы не удаляются), потом журнал.
            staging.listFiles()?.forEach { f ->
                f.inputStream().use { photos.restoreFrom(f.name, it) }
            }
            repository.save(batches)
            batches.size
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Готовит ZIP во временной папке и возвращает адрес для окна «Поделиться». */
    suspend fun prepareShare(): Uri = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, backupFileName())
        FileOutputStream(file).use { writeBackup(it) }
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    /** CSV для Excel: разделитель «;», кодировка UTF-8 с BOM, десятичная запятая. */
    suspend fun writeCsv(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val batches = repository.load().sortedBy { it.number }
        val ru = Locale.forLanguageTag("ru")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun num(v: Double) = String.format(ru, "%.1f", v)

        val sb = StringBuilder("\uFEFF")
        sb.append(
            "№;Название;Категория;Сырьё;В архиве;Закладка;Старт, г;Цель усушки, %;" +
                "Дата замера;Вес, г;Запах;Поверхность;Требует проверки\r\n",
        )
        batches.forEach { b ->
            val head = listOf(
                b.number.toString(), text(b.name), text(b.category), text(b.rawMaterial),
                if (b.archived) "да" else "нет", date.format(Date(b.createdAtEpochMs)),
                num(b.startWeightGrams), num(b.targetLossPercent),
            )
            val logs = b.logs.sortedBy { it.atEpochMs }
            if (logs.isEmpty()) {
                sb.append((head + listOf("", "", "", "", "")).joinToString(";")).append("\r\n")
            } else {
                logs.forEach { l ->
                    val row = head + listOf(
                        date.format(Date(l.atEpochMs)), num(l.weightGrams),
                        text(l.smellNote), text(l.surfaceNote), if (l.needsCheck) "да" else "нет",
                    )
                    sb.append(row.joinToString(";")).append("\r\n")
                }
            }
        }
        out.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        batches.size
    }

    // ───── служебное ─────

    private fun referencedPhotos(batches: List<Batch>): Set<String> = buildSet {
        batches.forEach { b ->
            b.photoFileName?.let { add(File(it).name) }
            b.logs.forEach { l -> l.photoFileName?.let { add(File(it).name) } }
        }
    }

    /** Текстовая ячейка CSV: экранирование и защита от формул Excel (=, +, -, @). */
    private fun text(value: String): String {
        var s = value.replace("\r", " ").replace("\n", " ")
        if (s.isNotEmpty() && s[0] in "=+-@\t") s = "'$s"
        return if (s.any { it == ';' || it == '"' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun InputStream.copyLimited(out: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw IOException("Файл внутри копии слишком большой")
            out.write(buffer, 0, n)
        }
        return total
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        const val JOURNAL = "journal.json"
        const val MAX_JOURNAL = 20L * 1024 * 1024
        const val MAX_PHOTO = 20L * 1024 * 1024
        const val MAX_TOTAL = 1024L * 1024 * 1024
    }
}