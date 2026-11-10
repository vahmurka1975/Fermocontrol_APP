package ru.fermocontrol.fermocontrol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fermocontrol.fermocontrol.model.Batch
import ru.fermocontrol.fermocontrol.model.Catalog

/**
 * Загружает обложку партии: своё фото, иначе заглушка каталога, иначе общая заглушка.
 * Используется в списке, архиве, PDF и уведомлении.
 */
class CoverLoader private constructor(context: Context) {

    private val app = context.applicationContext
    private val photos = PhotoStore(app)

    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private sealed interface Source {
        val key: String
        data class UserPhoto(val name: String) : Source { override val key get() = "u:$name" }
        data class Asset(val file: String) : Source { override val key get() = "a:$file" }
    }

    private fun sourceFor(batch: Batch): Source {
        val own = batch.photoFileName
        if (own != null && photos.isValidName(own) && photos.file(own).exists()) {
            return Source.UserPhoto(own)
        }
        val file = Catalog.byId(batch.productTypeId)?.photoFile ?: Catalog.DEFAULT_PHOTO_FILE
        return Source.Asset(file)
    }

    /** Ключ для remember в Compose. Меняется, когда должна смениться картинка. */
    fun keyFor(batch: Batch): String = sourceFor(batch).key

    fun cached(batch: Batch, maxSide: Int): Bitmap? =
        cache.get("${sourceFor(batch).key}@$maxSide")

    /** Обложка с длинной стороной не больше maxSide. null, если картинки нет вообще. */
    suspend fun load(batch: Batch, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val source = sourceFor(batch)
        loadSource(source, maxSide)
            ?: if (source is Source.Asset && source.file != Catalog.DEFAULT_PHOTO_FILE) {
                loadSource(Source.Asset(Catalog.DEFAULT_PHOTO_FILE), maxSide)
            } else {
                null
            }
    }

    /** Квадрат по центру для уведомления. Возвращает новый Bitmap. */
    suspend fun loadSquare(batch: Batch, sidePx: Int): Bitmap? {
        val src = load(batch, sidePx * 2) ?: return null
        return withContext(Dispatchers.Default) {
            val side = min(src.width, src.height)
            val cropped = Bitmap.createBitmap(
                src, (src.width - side) / 2, (src.height - side) / 2, side, side,
            )
            val scaled = Bitmap.createScaledBitmap(cropped, sidePx, sidePx, true)
            if (cropped !== src && cropped !== scaled) cropped.recycle()
            if (scaled === src) src.copy(src.config ?: Bitmap.Config.ARGB_8888, false) else scaled
        }
    }

    private fun loadSource(source: Source, maxSide: Int): Bitmap? {
        val cacheKey = "${source.key}@$maxSide"
        cache.get(cacheKey)?.let { return it }

        val bitmap = try {
            when (source) {
                is Source.UserPhoto -> decode({ photos.file(source.name).inputStream() }, maxSide)
                is Source.Asset -> decode(
                    { app.assets.open("$ASSET_DIR/${source.file}") },
                    maxSide,
                )
            }
        } catch (error: IOException) {
            null
        }

        if (bitmap != null) cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun decode(open: () -> InputStream, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open().use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        private const val ASSET_DIR = "catalog/placeholders"
        private const val CACHE_KB = 8 * 1024

        @Volatile private var instance: CoverLoader? = null

        fun get(context: Context): CoverLoader =
            instance ?: synchronized(this) {
                instance ?: CoverLoader(context).also { instance = it }
            }
    }
}