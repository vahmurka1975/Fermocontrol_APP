package ru.fermocontrol.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
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
    private const val KEY_AUTO_SYNC = "yandex_auto_sync"
    private const val KEY_LAST_SYNC = "last_cloud_sync"

    const val YANDEX_POLIGON_URL = "https://yandex.ru/dev/disk/poligon/"
    private const val YANDEX_DISK_INFO_URL = "https://cloud-api.yandex.net/v1/disk"
    private const val YANDEX_API_BASE = "https://cloud-api.yandex.net/v1/disk/resources"
    private const val YANDEX_FOLDER = "disk:/Fermocontrol"
    private const val YANDEX_FILE_DISK = "disk:/Fermocontrol/fermocontrol_backup.json"
    private const val YANDEX_FILE_APP = "app:/fermocontrol_backup.json"

    private val strictYandexTokenRegex =
        Regex("^(y0_[A-Za-z0-9_-]{25,}|AQAAA[A-Za-z0-9_-]{20,})$")
    private val urlAccessTokenRegex =
        Regex("(?:[#?&])access_token=([^&#\\s\"']+)")

    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

    /** Очищает токен от кавычек WebView, пробелов, префиксов OAuth/Bearer и URL-обёрток */
    fun normalizeToken(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim()

        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            val parsed = runCatching {
                val v = JSONTokener(s).nextValue()
                if (v == null || v === JSONObject.NULL) "" else v.toString()
            }.getOrNull()
            s = parsed ?: s.substring(1, s.length - 1)
        }

        s = s.trim().trim('"', '\'', '«', '»')
        if (s.equals("null", ignoreCase = true) || s.equals("undefined", ignoreCase = true)) {
            return ""
        }

        extractTokenFromUrl(s)?.let { return it }

        if (s.startsWith("{") && s.contains("access_token")) {
            runCatching {
                val jsonToken = JSONObject(s).optString("access_token", "")
                if (jsonToken.isNotBlank()) return normalizeToken(jsonToken)
            }
        }

        s = s.replace(Regex("(?i)^(oauth|bearer)\\s+"), "")
        s = s.replace(Regex("\\s+"), "")
        s = s.trim('"', '\'')
        return s
    }

    fun extractTokenFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val match = urlAccessTokenRegex.find(url) ?: return null
        val decoded = runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }
            .getOrDefault(match.groupValues[1])
            .trim('"', '\'', ' ')
        return decoded.takeIf { it.length >= 20 }
    }

    fun looksLikeYandexToken(token: String): Boolean =
        strictYandexTokenRegex.matches(normalizeToken(token))

    fun maskToken(token: String): String {
        val clean = normalizeToken(token)
        if (clean.isEmpty()) return "пусто"
        if (clean.length <= 10) return "$clean (${clean.length} симв.)"
        return "${clean.take(5)}…${clean.takeLast(4)} (${clean.length} симв.)"
    }

    fun getYandexToken(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_YANDEX_TOKEN, "")
            .orEmpty()
        val normalized = normalizeToken(raw)
        if (normalized != raw && raw.isNotEmpty()) {
            saveYandexToken(context, normalized)
        }
        return normalized
    }

    fun saveYandexToken(context: Context, token: String) {
        val cleaned = normalizeToken(token)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_YANDEX_TOKEN, cleaned)
            .apply()
    }

    fun clearYandexToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_YANDEX_TOKEN)
            .putBoolean(KEY_AUTO_SYNC, false)
            .apply()
    }

    fun getAutoSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SYNC, false)

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SYNC, enabled)
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

    /** Проверяет действительность токена на сервере Яндекс Диска и возвращает имя аккаунта */
    suspend fun verifyYandexToken(rawToken: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = normalizeToken(rawToken)
            check(token.isNotBlank()) { "Токен пуст." }

            val conn = (URL(YANDEX_DISK_INFO_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Authorization", "OAuth $token")
                setRequestProperty("Accept", "application/json")
            }

            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val userObj = JSONObject(body).optJSONObject("user")
                        val login = userObj?.optString("login").orEmpty()
                        val displayName = userObj?.optString("display_name").orEmpty()
                        when {
                            login.isNotBlank() -> login
                            displayName.isNotBlank() -> displayName
                            else -> "Яндекс Диск"
                        }
                    }
                    403 -> {
                        if (checkAppFolderAccess(token)) {
                            "Яндекс Диск (папка приложения)"
                        } else {
                            throw IllegalStateException(
                                "Токен принят, но у него нет прав доступа к Яндекс Диску (403)."
                            )
                        }
                    }
                    401 -> {
                        val details = readApiErrorMessage(conn)
                        throw IllegalStateException(
                            "Яндекс отклонил токен (401: $details). Отправлено: ${maskToken(token)}. " +
                                "Получите новый токен, начинающийся с y0_."
                        )
                    }
                    else -> {
                        val details = readApiErrorMessage(conn)
                        throw IllegalStateException("Ошибка проверки токена (HTTP $code): $details")
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun checkAppFolderAccess(token: String): Boolean {
        val encoded = URLEncoder.encode("app:/", "UTF-8")
        val conn = (URL("$YANDEX_API_BASE?path=$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "OAuth $token")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    private fun readApiErrorMessage(conn: HttpURLConnection): String {
        val raw = runCatching {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        if (raw.isBlank()) return "нет описания"
        return runCatching {
            val json = JSONObject(raw)
            json.optString("description").ifBlank { json.optString("message", raw) }
        }.getOrDefault(raw).take(160)
    }

    /** Отправка JSON-копии через системное меню Android (Яндекс Диск, Google Диск, Telegram) */
    suspend fun shareBackupFile(context: Context, fileName: String, bytes: ByteArray) {
        val uri = withContext(Dispatchers.IO) {
            val shareDir = File(context.cacheDir, "updates").apply { mkdirs() }
            shareDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("Fermocontrol_") && f.name.endsWith(".json")) {
                    f.delete()
                }
            }
            val outFile = File(shareDir, fileName)
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
                    val err = readApiErrorMessage(conn)
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
                    val err = readApiErrorMessage(putConn)
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
                401 -> {
                    val details = readApiErrorMessage(conn)
                    throw IllegalStateException(
                        "Неверный или истёкший OAuth-токен Яндекс Диска (401: $details). " +
                            "Отправлено: ${maskToken(token)}."
                    )
                }
                else -> {
                    val details = readApiErrorMessage(conn)
                    throw IllegalStateException("Ошибка доступа к Яндекс Диску (HTTP $code): $details")
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
                401 -> {
                    val details = readApiErrorMessage(conn)
                    throw IllegalStateException(
                        "Неверный или истёкший OAuth-токен Яндекс Диска (401: $details). " +
                            "Отправлено: ${maskToken(token)}."
                    )
                }
                else -> {
                    val details = readApiErrorMessage(conn)
                    throw IllegalStateException("Ошибка чтения с Яндекс Диска (HTTP $code): $details")
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

    suspend fun saveAutoBackup(context: Context, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "autobackup")
        if (!dir.exists()) dir.mkdirs()

        val latestFile = File(dir, "autobackup_latest.json")
        val prevFile = File(dir, "autobackup_previous.json")
        val tmpFile = File(dir, "autobackup_latest.tmp")

        if (latestFile.exists()) {
            val existing = runCatching { latestFile.readBytes() }.getOrNull()
            if (existing != null && existing.contentEquals(bytes)) {
                return@withContext false
            }
            val oldModified = latestFile.lastModified()
            latestFile.copyTo(prevFile, overwrite = true)
            if (oldModified > 0L) prevFile.setLastModified(oldModified)
        }

        tmpFile.writeBytes(bytes)
        tmpFile.copyTo(latestFile, overwrite = true)
        tmpFile.delete()
        true
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