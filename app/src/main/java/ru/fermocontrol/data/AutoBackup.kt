package ru.fermocontrol.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class AutoBackup(context: Context) {

    private val app = context.applicationContext
    private val dir = File(app.filesDir, "autobackups").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("autobackup", Context.MODE_PRIVATE)

    private var lastDigest: ByteArray? = null
    private var digestLoaded = false

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    @Synchronized
    fun snapshot(json: String, reason: String, force: Boolean = false) {
        if (!enabled && !force) return

        val bytes = json.toByteArray(Charsets.UTF_8)
        val digest = sha256(bytes)
        if (!force && latestDigest()?.contentEquals(digest) == true) return

        val stamp = SimpleDateFormat(STAMP, Locale.US).format(Date())
        val safeReason = reason.filter { it in 'a'..'z' }.ifEmpty { "edit" }
        val target = File(dir, "${stamp}_$safeReason$SUFFIX")
        val tmp = File(dir, "${target.name}.tmp")

        try {
            FileOutputStream(tmp).use { file ->
                GZIPOutputStream(file).use { it.write(bytes) }
                file.fd.sync()
            }
            if (tmp.renameTo(target)) lastDigest = digest else tmp.delete()
        } catch (e: IOException) {
            tmp.delete()
            return
        }
        prune()
    }

    @Synchronized
    fun list(): List<File> =
        dir.listFiles { f -> f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun read(file: File): String =
        GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun latestDigest(): ByteArray? {
        if (!digestLoaded) {
            digestLoaded = true
            lastDigest = list().firstOrNull()?.let { f ->
                runCatching { sha256(read(f).toByteArray(Charsets.UTF_8)) }.getOrNull()
            }
        }
        return lastDigest
    }

    private fun prune() {
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }

        val files = list()
        val keep = HashSet<File>(files.take(30))
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000
        val seenDays = HashSet<String>()
        for (f in files) {
            if (f.name.endsWith("_prerestore$SUFFIX")) {
                keep += f
            } else if (f.lastModified() >= cutoff && seenDays.add(f.name.substring(0, 8))) {
                keep += f
            }
        }
        files.filterNot { it in keep }.forEach { it.delete() }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        const val SUFFIX = ".json.gz"
        const val STAMP = "yyyyMMdd_HHmmss"
    }
}