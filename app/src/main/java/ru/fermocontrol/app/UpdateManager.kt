package ru.fermocontrol.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateManager {
    private const val GITHUB_REPO = "vahmurka1975/Fermocontrol_APP"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    suspend fun checkForUpdate(currentVersion: String = "1.0.2"): UpdateInfo =
        checkUpdate(currentVersion)

    suspend fun checkUpdate(currentVersion: String = "1.0.2"): UpdateInfo = withContext(Dispatchers.IO) {
        val url = URL(API_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Fermocontrol-Android-App")
            connectTimeout = 8000
            readTimeout = 8000
        }

        if (conn.responseCode != 200) {
            return@withContext UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                releaseNotes = "Статус проверки GitHub Releases: HTTP ${conn.responseCode}",
                downloadUrl = ""
            )
        }

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
        val bodyText = json.optString("body", "Нет описания изменений")

        val assets = json.optJSONArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }

        val hasUpdate = isNewer(tagName, currentVersion) && apkUrl.isNotBlank()

        UpdateInfo(
            hasUpdate = hasUpdate,
            latestVersion = tagName.ifBlank { currentVersion },
            releaseNotes = bodyText,
            downloadUrl = apkUrl
        )
    }

    private fun isNewer(remote: String, current: String): Boolean {
        if (remote.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            var currentUrl = downloadUrl
            var redirects = 0
            var actualConn: HttpURLConnection

            while (true) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Fermocontrol-Android-App")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IllegalStateException("Пустой адрес перенаправления")
                    conn.disconnect()
                    currentUrl = location
                    redirects++
                    if (redirects > 5) throw IllegalStateException("Слишком много перенаправлений")
                } else {
                    actualConn = conn
                    break
                }
            }

            if (actualConn.responseCode != 200) {
                throw IllegalStateException("Ошибка скачивания APK (HTTP ${actualConn.responseCode})")
            }

            val fileLength = actualConn.contentLength
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkFile = File(cacheDir, "fermocontrol-update.apk")
            if (apkFile.exists()) apkFile.delete()

            actualConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val percent = ((total * 100) / fileLength).toInt()
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                val apkUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            }
        }
    }
}