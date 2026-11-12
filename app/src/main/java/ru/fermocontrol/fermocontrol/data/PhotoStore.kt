package ru.fermocontrol.fermocontrol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Хранилище фотографий журнала: files/photos/<имя>.jpg.
 * В JSON журнала попадает только имя файла.
 */
class PhotoStore(context: Context) {

    private val app = context.applicationContext
    private val photoDir = File(app.filesDir, "photos")
    private val cameraDir = File(app.cacheDir, "camera")

    /** Файл по имени из журнала. Берём только имя, без путей. */
    fun file(name: String): File = File(photoDir, File(name).name)

    /** Временный адрес, в который системная камера запишет снимок. */
    fun newCameraUri(): Uri {
        cameraDir.mkdirs()
        val now = System.currentTimeMillis()
        cameraDir.listFiles()
            ?.filter { now - it.lastModified() > DAY_MS }
            ?.forEach { it.delete() }

        val target = File.createTempFile("shot_", ".jpg", cameraDir)
        return FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            target,
        )
    }

    /** Уменьшенное изображение для превью. Поворот уже учтён. */
    suspend fun loadPreview(uri: Uri): Bitmap =
        withContext(Dispatchers.IO) { decodeOriented(uri, PREVIEW_MAX_SIDE) }

    /** Сжимает фото и сохраняет в хранилище. Возвращает имя файла. */
    suspend fun saveFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeOriented(uri, SAVE_MAX_SIDE)
        try {
            photoDir.mkdirs()

            val name = "p_${UUID.randomUUID()}.jpg"
            val target = file(name)
            val tmp = File(photoDir, "$name.tmp")

            try {
                FileOutputStream(tmp).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                        throw IOException("Не удалось сжать фото")
                    }
                    out.flush()
                    out.fd.sync()
                }
                if (!tmp.renameTo(target)) {
                    throw IOException("Не удалось сохранить файл фото")
                }
            } catch (error: Exception) {
                tmp.delete()
                throw error
            }

            name
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(name: String) {
        file(name).delete()
    }
        /** Обложка: центральный квадрат 512 px в WebP. Возвращает имя файла. */
    suspend fun saveCover(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = decodeOriented(uri, COVER_DECODE_SIDE)
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source, (source.width - side) / 2, (source.height - side) / 2, side, side,
        )
        val cover = if (side > COVER_SIDE) {
            Bitmap.createScaledBitmap(cropped, COVER_SIDE, COVER_SIDE, true)
        } else {
            cropped
        }
        try {
            photoDir.mkdirs()
            val name = "c_${UUID.randomUUID()}.webp"
            val tmp = File(photoDir, "$name.tmp")
            try {
                FileOutputStream(tmp).use { out ->
                    if (!cover.compress(coverFormat(), 85, out)) {
                        throw IOException("Не удалось сжать обложку")
                    }
                    out.flush()
                    out.fd.sync()
                }
                if (!tmp.renameTo(file(name))) throw IOException("Не удалось сохранить обложку")
            } catch (error: Exception) {
                tmp.delete()
                throw error
            }
            name
        } finally {
            if (cover !== cropped) cover.recycle()
            if (cropped !== source) cropped.recycle()
            source.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun coverFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP
		
	/** Допустимы только имена, которые создаёт saveFrom. Остальное при восстановлении отбрасывается. */
    fun isValidName(name: String): Boolean = PHOTO_NAME.matches(name)

    /** Кладёт готовый JPEG из резервной копии в хранилище. */
    fun restoreFrom(name: String, input: InputStream) {
        require(isValidName(name)) { "Недопустимое имя фото" }
        photoDir.mkdirs()
        val tmp = File(photoDir, "$name.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                input.copyTo(out)
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(file(name))) throw IOException("Не удалось сохранить фото из копии")
        } catch (error: Exception) {
            tmp.delete()
            throw error
        }
    }
    
	private fun decodeOriented(uri: Uri, maxSide: Int): Bitmap {
        val resolver = app.contentResolver

        fun open(): InputStream = try {
            resolver.openInputStream(uri)
                ?: throw IOException("Не удалось открыть фото")
        } catch (error: SecurityException) {
            throw IOException("Нет доступа к выбранному фото. Выберите его заново.", error)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Файл не является изображением")
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Не удалось прочитать изображение")

        val orientation = try {
            open().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (error: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) matrix.postScale(scale, scale)

        if (matrix.isIdentity) return decoded

        val result = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true,
        )
        if (result !== decoded) decoded.recycle()
        return result
    }

    private companion object {
        const val SAVE_MAX_SIDE = 1600
        const val PREVIEW_MAX_SIDE = 480
        const val DAY_MS = 86_400_000L
		const val COVER_SIDE = 512
        const val COVER_DECODE_SIDE = 1024
		val PHOTO_NAME = Regex("[pc]_[0-9a-fA-F-]{36}\\.(?:jpg|webp)")
    }
}