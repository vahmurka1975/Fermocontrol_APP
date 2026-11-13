package ru.fermocontrol.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ежедневная копия журнала и фотографий (ZIP) в папку, выбранную пользователем. */
class ExternalBackup(
    context: Context,
    private val backups: BackupManager,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("external_backup", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    var enabled by mutableStateOf(prefs.getBoolean(ENABLED, false) && treeUri() != null)
        private set
    var folderName by mutableStateOf(prefs.getString(FOLDER_NAME, null))
        private set
    var lastSuccessMs by mutableStateOf(prefs.getLong(LAST_OK, 0L))
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var running by mutableStateOf(false)
        private set

    /** Вызывать из репозитория после каждой успешной записи журнала. */
    fun onJournalChanged() {
        prefs.edit { putLong(SEQ, prefs.getLong(SEQ, 0L) + 1) }
        maybeRun()
    }

    /** Вызывать при запуске приложения. */
    fun onAppStart() = maybeRun()

    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        app.contentResolver.takePersistableUriPermission(uri, flags)
        val old = treeUri()
        if (old != null && old != uri) {
            runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        val name = runCatching { DocumentFile.fromTreeUri(app, uri)?.name }.getOrNull()
        prefs.edit {
            putString(TREE, uri.toString())
            putString(FOLDER_NAME, name)
            putBoolean(ENABLED, true)
            putLong(LAST_OK, 0L)
        }
        folderName = name
        lastSuccessMs = 0L
        enabled = true
        error = null
        maybeRun(force = true)
    }

    @JvmName("applyEnabled")
fun setEnabled(value: Boolean) {
    if (value && treeUri() == null) return
    prefs.edit { putBoolean(ENABLED, value) }
    enabled = value
    if (value) maybeRun()
}

    fun runNow() = maybeRun(force = true)

    private fun maybeRun(force: Boolean = false) {
        if (!enabled || running) return
        val changed = prefs.getLong(SEQ, 0L) != prefs.getLong(BACKED_SEQ, -1L)
        val elapsed = System.currentTimeMillis() - lastSuccessMs
        val due = force || (changed && (elapsed !in 0 until DAY_MS))
        if (due) scope.launch { runBackup() }
    }

    private suspend fun runBackup() = mutex.withLock {
        if (!enabled) return@withLock
        running = true
        error = null
        val seqAtStart = prefs.getLong(SEQ, 0L)
        var created: DocumentFile? = null
        try {
            val tree = treeUri() ?: throw IOException("Папка не выбрана")
            val granted = app.contentResolver.persistedUriPermissions
                .any { it.uri == tree && it.isWritePermission }
            if (!granted) throw IOException("Нет доступа к папке. Выберите её заново")
            val dir = DocumentFile.fromTreeUri(app, tree)
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?: throw IOException("Папка недоступна. Выберите её заново")

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = dir.createFile("application/zip", "$PREFIX$stamp.zip")
                ?: throw IOException("Не удалось создать файл в папке")
            created = file
            app.contentResolver.openOutputStream(file.uri, "wt")
                ?.use { backups.writeBackup(it) }
                ?: throw IOException("Не удалось открыть файл для записи")
            verify(file)

            val now = System.currentTimeMillis()
            prefs.edit {
                putLong(LAST_OK, now)
                putLong(BACKED_SEQ, seqAtStart)
            }
            lastSuccessMs = now
            created = null
            prune(dir)
        } catch (e: CancellationException) {
            created?.delete()
            throw e
        } catch (e: Exception) {
            created?.delete()
            error = e.message ?: "Копия в папку не сделана"
        } finally {
            running = false
        }
    }

    /** Читает ZIP целиком: так проверяются контрольные суммы и обрыв записи. */
    private fun verify(file: DocumentFile) {
        if (file.length() <= 0L) throw IOException("Файл копии получился пустым")
        var hasJournal = false
        val buffer = ByteArray(64 * 1024)
        val input = app.contentResolver.openInputStream(file.uri)
            ?: throw IOException("Не удалось проверить копию")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.name.startsWith("photos/")) hasJournal = true
                while (zip.read(buffer) != -1) { /* читаем до конца записи */ }
            }
        }
        if (!hasJournal) throw IOException("В копии нет журнала")
    }

    private fun prune(dir: DocumentFile) {
        dir.listFiles()
            .filter { f ->
                val n = f.name.orEmpty()
                f.isFile && n.startsWith(PREFIX) && n.endsWith(".zip")
            }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    private fun treeUri(): Uri? = prefs.getString(TREE, null)?.let(Uri::parse)

    private companion object {
        const val PREFIX = "fermocontrol_auto_"
        const val KEEP = 7
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val ENABLED = "enabled"
        const val TREE = "tree"
        const val FOLDER_NAME = "folder_name"
        const val LAST_OK = "last_ok"
        const val SEQ = "seq"
        const val BACKED_SEQ = "backed_seq"
    }
}