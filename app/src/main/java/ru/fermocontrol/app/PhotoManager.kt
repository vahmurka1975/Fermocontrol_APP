package ru.fermocontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Элемент ленты фотодневника (начальное фото или фото взвешивания)
 */
data class PhotoDiaryItem(
    val photoPath: String,
    val title: String,
    val subtitle: String,
    val timestamp: Long = 0L
)

object PhotoManager {
    private const val PHOTOS_DIR = "photos"
    private const val MAX_IMAGE_DIMENSION = 1280
    private const val JPEG_QUALITY = 82

    private val memoryCache = LruCache<String, ImageBitmap>(40)

    fun getPhotosDir(context: Context): File {
        return File(context.filesDir, PHOTOS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    fun resolvePhotoFile(context: Context, pathOrName: String?): File? {
        if (pathOrName.isNullOrBlank()) return null
        val direct = File(pathOrName)
        if (direct.isAbsolute && direct.exists()) return direct
        val inPhotos = File(getPhotosDir(context), direct.name)
        return if (inPhotos.exists()) inPhotos else null
    }

    fun createTempCameraUri(context: Context): Pair<File, Uri> {
        val tempDir = File(context.cacheDir, "camera_temp").apply {
            if (!exists()) mkdirs()
        }
        val tempFile = File(tempDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return tempFile to uri
    }

    /**
     * Сжимает фото из камеры или галереи с учётом EXIF-поворота и сохраняет во внутреннюю память.
     * Возвращает имя сохранённого файла (например, "photo_1730200000000.jpg").
     */
    suspend fun importAndCompressPhoto(context: Context, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempRaw = File(context.cacheDir, "raw_import_${System.currentTimeMillis()}.tmp")
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        FileOutputStream(tempRaw).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@runCatching null

                    val rotation = readExifRotation(tempRaw.absolutePath)

                    val boundsOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(tempRaw.absolutePath, boundsOptions)
                    val srcW = boundsOptions.outWidth
                    val srcH = boundsOptions.outHeight
                    if (srcW <= 0 || srcH <= 0) return@runCatching null

                    var sampleSize = 1
                    while (max(srcW / sampleSize, srcH / sampleSize) > MAX_IMAGE_DIMENSION * 2) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = BitmapFactory.decodeFile(tempRaw.absolutePath, decodeOptions)
                        ?: return@runCatching null

                    val processed = scaleAndRotateBitmap(decoded, MAX_IMAGE_DIMENSION, rotation)
                    if (processed !== decoded) {
                        decoded.recycle()
                    }

                    val fileName = "photo_${System.currentTimeMillis()}.jpg"
                    val targetFile = File(getPhotosDir(context), fileName)
                    FileOutputStream(targetFile).use { out ->
                        processed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                        out.flush()
                    }
                    processed.recycle()

                    fileName
                } finally {
                    if (tempRaw.exists()) tempRaw.delete()
                }
            }.getOrNull()
        }

    suspend fun loadBitmap(context: Context, pathOrName: String?): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (pathOrName.isNullOrBlank()) return@withContext null
            memoryCache.get(pathOrName)?.let { return@withContext it }

            val file = resolvePhotoFile(context, pathOrName) ?: return@withContext null
            runCatching {
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
                val imageBitmap = bmp.asImageBitmap()
                memoryCache.put(pathOrName, imageBitmap)
                imageBitmap
            }.getOrNull()
        }

    fun deletePhoto(context: Context, pathOrName: String?) {
        if (pathOrName.isNullOrBlank()) return
        memoryCache.remove(pathOrName)
        runCatching {
            resolvePhotoFile(context, pathOrName)?.delete()
        }
    }

    private fun readExifRotation(filePath: String): Int {
        return runCatching {
            val exif = ExifInterface(filePath)
            when (
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    private fun scaleAndRotateBitmap(source: Bitmap, maxDim: Int, rotationDegrees: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longest = max(width, height)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest.toFloat() else 1f

        if (scale == 1f && rotationDegrees == 0) return source

        val matrix = Matrix().apply {
            if (scale != 1f) {
                postScale(scale, scale)
            }
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
        }
        return Bitmap.createBitmap(
            source,
            0,
            0,
            width,
            height,
            matrix,
            true
        )
    }
}

/**
 * Миниатюра фотографии с асинхронной подгрузкой из памяти
 */
@Composable
fun PhotoThumbnail(
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null
) {
    if (photoPath.isNullOrBlank()) return
    val context = LocalContext.current
    var bitmap by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoPath) {
        bitmap = PhotoManager.loadBitmap(context, photoPath)
    }

    val loaded = bitmap
    if (loaded != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                )
                .let { mod ->
                    if (onClick != null) mod.clickable { onClick() } else mod
                }
        ) {
            Image(
                bitmap = loaded,
                contentDescription = "Фото продукта",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Панель прикрепления фото (Камера + Галерея + предпросмотр) для диалогов создания продукта и взвешивания
 */
@Composable
fun PhotoPickerSection(
    photoPath: String?,
    onPhotoChanged: (String?) -> Unit,
    label: String = "Фотография",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showFullPreview by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        val tempFile = pendingCameraFile
        if (success && uri != null) {
            isProcessing = true
            scope.launch {
                val savedName = PhotoManager.importAndCompressPhoto(context, uri)
                tempFile?.delete()
                isProcessing = false
                if (savedName != null) {
                    onPhotoChanged(savedName)
                } else {
                    Toast.makeText(context, "Не удалось обработать снимок", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            tempFile?.delete()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val savedName = PhotoManager.importAndCompressPhoto(context, uri)
                isProcessing = false
                if (savedName != null) {
                    onPhotoChanged(savedName)
                } else {
                    Toast.makeText(context, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!photoPath.isNullOrBlank()) {
                PhotoThumbnail(
                    photoPath = photoPath,
                    size = 72.dp,
                    onClick = { showFullPreview = true }
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val (file, uri) = PhotoManager.createTempCameraUri(context)
                                pendingCameraFile = file
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Не удалось открыть камеру",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Камера")
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Галерея")
                    }
                }

                if (!photoPath.isNullOrBlank()) {
                    TextButton(
                        onClick = { onPhotoChanged(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Убрать фото",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    if (showFullPreview && !photoPath.isNullOrBlank()) {
        FullScreenPhotoGalleryDialog(
            items = listOf(
                PhotoDiaryItem(
                    photoPath = photoPath,
                    title = label,
                    subtitle = ""
                )
            ),
            initialIndex = 0,
            onDismiss = { showFullPreview = false }
        )
    }
}

/**
 * Горизонтальная лента фотодневника продукта (от закладки до последнего взвешивания)
 */
@Composable
fun ProductPhotoDiaryStrip(
    items: List<PhotoDiaryItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Фотодневник (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Нажмите для сравнения",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { selectedIndex = index }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PhotoThumbnail(
                        photoPath = item.photoPath,
                        size = 84.dp,
                        onClick = { selectedIndex = index }
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    val openIdx = selectedIndex
    if (openIdx != null && openIdx in items.indices) {
        FullScreenPhotoGalleryDialog(
            items = items,
            initialIndex = openIdx,
            onDismiss = { selectedIndex = null }
        )
    }
}

/**
 * Полноэкранный просмотрщик фотографий с зумом двумя пальцами и переключением по датам
 */
@Composable
fun FullScreenPhotoGalleryDialog(
    items: List<PhotoDiaryItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    var currentIndex by remember(initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex))
    }
    val currentItem = items[currentIndex]

    var bitmap by remember(currentItem.photoPath) { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(currentIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(currentIndex) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentItem.photoPath) {
        bitmap = PhotoManager.loadBitmap(context, currentItem.photoPath)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.94f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Верхняя шапка с заголовком и кнопкой закрытия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentItem.subtitle.isNotBlank()) {
                            Text(
                                text = currentItem.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    FilledTonalButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }

                // Область изображения с поддержкой зума
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(currentIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val loaded = bitmap
                    if (loaded != null) {
                        Image(
                            bitmap = loaded,
                            contentDescription = currentItem.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                // Нижняя панель переключения между снимками разных дат
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0
                    ) {
                        Text("← Раньше", color = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${currentIndex + 1} из ${items.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        if ((scale * 100).roundToInt() > 105) {
                            TextButton(
                                onClick = {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            ) {
                                Text("Сбросить масштаб", color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { if (currentIndex < items.lastIndex) currentIndex++ },
                        enabled = currentIndex < items.lastIndex
                    ) {
                        Text("Позже →", color = Color.White)
                    }
                }
            }
        }
    }
}