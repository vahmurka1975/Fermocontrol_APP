package ru.fermocontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Возвращает актуальную фотографию продукта:
 * последнее фото из взвешиваний, либо исходное фото продукта при закладке.
 */
fun Product.latestPhotoPath(): String? {
    val latestWeighingPhoto = weighings.lastOrNull { !it.photoPath.isNullOrBlank() }?.photoPath
    return latestWeighingPhoto ?: photoPath?.takeIf { it.isNotBlank() }
}

data class PhotoDiaryEntry(
    val path: String,
    val title: String,
    val subtitle: String,
    val note: String = ""
)

/**
 * Собирает хронологическую ленту фотографий продукта (от закладки до последнего взвешивания).
 */
fun Product.photoDiaryEntries(initialWeight: Double? = null): List<PhotoDiaryEntry> {
    val entries = mutableListOf<PhotoDiaryEntry>()
    val startPhoto = photoPath?.takeIf { it.isNotBlank() }
    if (startPhoto != null) {
        val weightLabel = if (initialWeight != null && initialWeight > 0) {
            "Начальный вес: ${initialWeight.roundToInt()} г"
        } else {
            "Исходное состояние"
        }
        entries += PhotoDiaryEntry(
            path = startPhoto,
            title = "Закладка",
            subtitle = weightLabel
        )
    }

    weighings.forEachIndexed { index, w ->
        val path = w.photoPath?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
        val lossPart = if (initialWeight != null && initialWeight > 0) {
            val lossPct = ((initialWeight - w.weight) / initialWeight) * 100.0
            String.format(Locale.US, " (%.1f%%)", lossPct)
        } else {
            ""
        }
        val climatePart = buildList {
            w.temperature?.let { add("${it}°C") }
            w.humidity?.let { add("${it.roundToInt()}%") }
        }.joinToString(", ").let { if (it.isNotEmpty()) " • $it" else "" }

        entries += PhotoDiaryEntry(
            path = path,
            title = "Взвешивание #${index + 1} • ${w.at.take(10)}",
            subtitle = "${w.weight.roundToInt()} г$lossPart$climatePart",
            note = w.note
        )
    }
    return entries
}

object PhotoStorage {
    private const val PHOTOS_DIR = "photos"
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85

    fun copyFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, bounds)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, opts)
                ?: return null
            saveBitmap(context, bitmap)
        }.getOrNull()
    }

    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return runCatching {
            val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
            val file = File(dir, "photo_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    fun loadSampledBitmap(path: String?, maxDim: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while (longest / inSampleSize > maxDim) {
            inSampleSize *= 2
        }
        return max(1, inSampleSize)
    }
}

@Composable
private fun rememberLoadedBitmap(path: String?, maxDim: Int = 512): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path, key2 = maxDim) {
        value = withContext(Dispatchers.IO) {
            PhotoStorage.loadSampledBitmap(path, maxDim)
        }
    }
    return bitmap
}

/**
 * Миниатюра фото для карточки продукта в списке.
 */
@Composable
fun ProductPhotoThumbnail(
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val bitmap = rememberLoadedBitmap(photoPath, maxDim = 256)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Фото продукта",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

/**
 * Поле выбора/съёмки фотографии для диалогов добавления, редактирования и взвешивания.
 */
@Composable
fun PhotoPickerField(
    photoPath: String?,
    onPhotoPathChange: (String?) -> Unit,
    label: String = "Фотография"
) {
    val context = LocalContext.current
    var errorText by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = PhotoStorage.copyFromUri(context, uri)
            if (savedPath != null) {
                errorText = null
                onPhotoPathChange(savedPath)
            } else {
                errorText = "Не удалось сохранить выбранное фото"
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedPath = PhotoStorage.saveBitmap(context, bitmap)
            if (savedPath != null) {
                errorText = null
                onPhotoPathChange(savedPath)
            } else {
                errorText = "Не удалось сохранить снимок камеры"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            val previewBitmap = rememberLoadedBitmap(photoPath, maxDim = 512)
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Галерея")
                }
                OutlinedButton(
                    onClick = {
                        runCatching { cameraLauncher.launch(null) }
                            .onFailure { errorText = "Камера недоступна на устройстве" }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Камера")
                }
                if (!photoPath.isNullOrBlank()) {
                    TextButton(onClick = { onPhotoPathChange(null) }) {
                        Text("Убрать")
                    }
                }
            }

            errorText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Секция «Фотодневник» для окна подробностей продукта (с лентой и сравнением «До / После»).
 */
@Composable
fun PhotoDiarySection(
    product: Product,
    initialWeight: Double? = null
) {
    val entries = remember(product, initialWeight) {
        product.photoDiaryEntries(initialWeight)
    }
    var selectedEntry by remember { mutableStateOf<PhotoDiaryEntry?>(null) }

    if (entries.isEmpty()) {
        Text(
            text = "Фотодневник пока пуст. Прикрепите фото при взвешивании или в редактировании продукта.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Фотодневник (${entries.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (entries.size >= 2) {
            val first = entries.first()
            val last = entries.last()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Сравнение: Начало → Сейчас",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhotoCompareTile(
                            entry = first,
                            badge = "Было",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEntry = first }
                        )
                        PhotoCompareTile(
                            entry = last,
                            badge = "Стало",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedEntry = last }
                        )
                    }
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries) { entry ->
                PhotoDiaryCard(
                    entry = entry,
                    onClick = { selectedEntry = entry }
                )
            }
        }
    }

    selectedEntry?.let { entry ->
        Dialog(onDismissRequest = { selectedEntry = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val fullBitmap = rememberLoadedBitmap(entry.path, maxDim = 1400)
                    if (fullBitmap != null) {
                        Image(
                            bitmap = fullBitmap.asImageBitmap(),
                            contentDescription = entry.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    if (entry.note.isNotBlank()) {
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { selectedEntry = null }) {
                            Text("Закрыть")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCompareTile(
    entry: PhotoDiaryEntry,
    badge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bmp = rememberLoadedBitmap(entry.path, maxDim = 400)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = entry.subtitle,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PhotoDiaryCard(
    entry: PhotoDiaryEntry,
    onClick: () -> Unit
) {
    val bmp = rememberLoadedBitmap(entry.path, maxDim = 400)
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Spacer(modifier = Modifier.height(105.dp))
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Long.take(n: Int): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(this))
        .take(n)
