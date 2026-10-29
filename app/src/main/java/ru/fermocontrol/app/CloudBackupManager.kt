package ru.fermocontrol.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AutoBackupItem(
    val id: String,
    val title: String,
    val updatedAt: String,
    val file: File
)

object CloudBackupManager {
    private const val PREFS_NAME = "fermocontrol_cloud_prefs"
    private const val KEY_YANDEX_TOKEN = "yandex_oauth_token"
    private const val KEY_LAST_SYNC = "last_cloud_sync"

    private const val YANDEX_API_BASE = "https://cloud-api.yandex.net/v1/disk/resources"
    private const val YANDEX_FOLDER = "disk:/Fermocontrol"
    private const val YANDEX_FILE_DISK = "disk:/Fermocontrol/fermocontrol_backup.json"
    private const val YANDEX_FILE_APP = "app:/fermocontrol_backup.json"

    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

    fun getYandexToken(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_YANDEX_TOKEN, "")
            ?.trim()
            .orEmpty()

    fun saveYandexToken(context: Context, token: String) {
        val cleaned = token.trim()
            .removePrefix("OAuth ")
            .removePrefix("Bearer ")
            .trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_YANDEX_TOKEN, cleaned)
            .apply()
    }

    fun clearYandexToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_YANDEX_TOKEN)
            .apply()
    }

    fun getLastSyncTime(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SYNC, null)

    private fun saveLastSyncTime(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SYNC, value)
            .apply()
    }

    /** Отправка JSON-копии через системное меню Android (Яндекс Диск, Google Диск, Telegram и др.) */
    suspend fun shareBackupFile(context: Context, fileName: String, bytes: ByteArray) {
        val uri = withContext(Dispatchers.IO) {
            val baseDir = context.externalCacheDir ?: context.cacheDir
            baseDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("Fermocontrol_") && f.name.endsWith(".json")) {
                    f.delete()
                }
            }
            val outFile = File(baseDir, fileName)
            outFile.writeBytes(bytes)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        }

        withContext(Dispatchers.Main) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Резервная копия ФермоКонтроль ($fileName)")
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Сохранить копию в облако").apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        }
    }

    /** Выгрузка JSON на Яндекс Диск по OAuth-токену */
    suspend fun uploadToYandexDisk(context: Context, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val token = getYandexToken(context)
            check(token.isNotBlank()) { "Сначала укажите OAuth-токен Яндекс Диска." }

            val targetPath = resolveWritablePath(token)
            val encodedPath = URLEncoder.encode(targetPath, "UTF-8")
            val reqUrl = "$YANDEX_API_BASE/upload?path=$encodedPath&overwrite=true"

            val conn = (URL(reqUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Authorization", "OAuth $token")
                setRequestProperty("Accept", "application/json")
            }

            val uploadHref = try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Не удалось получить ссылку загрузки (HTTP $code): $err")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).getString("href")
            } finally {
                conn.disconnect()
            }

            val putConn = (URL(uploadHref).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setFixedLengthStreamingMode(bytes.size)
            }

            try {
                putConn.outputStream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                val putCode = putConn.responseCode
                if (putCode !in 200..299) {
                    val err = putConn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Ошибка записи файла на Яндекс Диск (HTTP $putCode): $err")
                }
            } finally {
                putConn.disconnect()
            }

            val nowStr = LocalDateTime.now().format(timeFormat)
            saveLastSyncTime(context, nowStr)
            nowStr
        }

    /** Скачивание JSON с Яндекс Диска по OAuth-токену */
    suspend fun downloadFromYandexDisk(context: Context): ByteArray =
        withContext(Dispatchers.IO) {
            val token = getYandexToken(context)
            check(token.isNotBlank()) { "Сначала укажите OAuth-токен Яндекс Диска." }

            val downloadHref = requestDownloadHref(token, YANDEX_FILE_DISK)
                ?: requestDownloadHref(token, YANDEX_FILE_APP)
                ?: throw IllegalStateException("Копия на Яндекс Диске не найдена. Сначала выполните выгрузку.")

            val bytes = downloadFollowingRedirects(downloadHref)
            val nowStr = LocalDateTime.now().format(timeFormat)
            saveLastSyncTime(context, nowStr)
            bytes
        }

    private fun resolveWritablePath(token: String): String {
        val encodedFolder = URLEncoder.encode(YANDEX_FOLDER, "UTF-8")
        val conn = (URL("$YANDEX_API_BASE?path=$encodedFolder").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "OAuth $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            when (val code = conn.responseCode) {
                201, 409 -> YANDEX_FILE_DISK
                403 -> YANDEX_FILE_APP
                401 -> throw IllegalStateException("Неверный или недействительный OAuth-токен Яндекс Диска (401).")
                else -> {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Ошибка доступа к Яндекс Диску (HTTP $code): $err")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun requestDownloadHref(token: String, remotePath: String): String? {
        val encodedPath = URLEncoder.encode(remotePath, "UTF-8")
        val conn = (URL("$YANDEX_API_BASE/download?path=$encodedPath").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "OAuth $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            when (val code = conn.responseCode) {
                in 200..299 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body).getString("href")
                }
                403, 404 -> null
                401 -> throw IllegalStateException("Неверный или недействительный OAuth-токен Яндекс Диска (401).")
                else -> {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Ошибка чтения с Яндекс Диска (HTTP $code): $err")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFollowingRedirects(initialUrl: String): ByteArray {
        var currentUrl = initialUrl
        var redirects = 0
        while (true) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 20000
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Пустой адрес перенаправления при скачивании копии.")
                }
                currentUrl = location
                redirects++
                if (redirects > 5) {
                    throw IllegalStateException("Слишком много перенаправлений при скачивании копии.")
                }
            } else {
                return try {
                    if (code !in 200..299) {
                        throw IllegalStateException("Ошибка скачивания копии с Яндекс Диска (HTTP $code).")
                    }
                    conn.inputStream.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    /** Автоматическое сохранение двух последних различающихся локальных снапшотов */
    suspend fun saveAutoBackup(context: Context, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "autobackup")
        if (!dir.exists()) dir.mkdirs()

        val latestFile = File(dir, "autobackup_latest.json")
        val prevFile = File(dir, "autobackup_previous.json")
        val tmpFile = File(dir, "autobackup_latest.tmp")

        if (latestFile.exists()) {
            val existing = runCatching { latestFile.readBytes() }.getOrNull()
            if (existing != null && existing.contentEquals(bytes)) {
                return@withContext
            }
            val oldModified = latestFile.lastModified()
            latestFile.copyTo(prevFile, overwrite = true)
            if (oldModified > 0L) prevFile.setLastModified(oldModified)
        }

        tmpFile.writeBytes(bytes)
        tmpFile.copyTo(latestFile, overwrite = true)
        tmpFile.delete()
    }

    suspend fun listAutoBackups(context: Context): List<AutoBackupItem> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "autobackup")
        val latestFile = File(dir, "autobackup_latest.json")
        val prevFile = File(dir, "autobackup_previous.json")
        val result = mutableListOf<AutoBackupItem>()

        if (latestFile.exists() && latestFile.length() > 0L) {
            result += AutoBackupItem(
                id = "latest",
                title = "Последняя автокопия",
                updatedAt = formatEpoch(latestFile.lastModified()),
                file = latestFile
            )
        }
        if (prevFile.exists() && prevFile.length() > 0L) {
            result += AutoBackupItem(
                id = "previous",
                title = "Предыдущая автокопия",
                updatedAt = formatEpoch(prevFile.lastModified()),
                file = prevFile
            )
        }
        result
    }

    suspend fun readAutoBackup(item: AutoBackupItem): ByteArray = withContext(Dispatchers.IO) {
        item.file.readBytes()
    }

    private fun formatEpoch(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(timeFormat)
}