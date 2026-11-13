package ru.fermocontrol.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fermocontrol.model.Batch
import ru.fermocontrol.model.Catalog
import ru.fermocontrol.model.computeMetrics
import ru.fermocontrol.model.computeStatus
import ru.fermocontrol.model.nextActionText
import ru.fermocontrol.ui.fmtDateShort
import ru.fermocontrol.ui.fmtGrams
import ru.fermocontrol.ui.fmtPercentChange
import ru.fermocontrol.ui.theme.Amber
import ru.fermocontrol.ui.theme.LocalAppPalette
import ru.fermocontrol.ui.theme.WarnFill
import ru.fermocontrol.ui.theme.WarnInk
import ru.fermocontrol.ui.theme.WarnLine

/** Карточка продукта: структура .product-card макета v0.4.1. */
@Composable
fun BatchCard(
    batch: Batch,
    onClick: () -> Unit,
    onWeigh: () -> Unit,
    nowEpochMs: Long = System.currentTimeMillis(),
    /** Файл фото из журнала по имени. Если null, обложкой служит заглушка каталога. */
    photoFile: ((String) -> File)? = null,
) {
    val p = LocalAppPalette.current
    val metrics = computeMetrics(batch, nowEpochMs)
    val status = computeStatus(batch, nowEpochMs)
    val warning = status.needsAttention
    val shape = RoundedCornerShape(20.dp)
    val lastLog = batch.logs.maxByOrNull { it.atEpochMs }
    val start = batch.startWeightGrams
    val rawLoss = if (start > 0.0) (start - metrics.currentWeightGrams) / start * 100.0 else 0.0

    val dayLabel = if (metrics.daysElapsed == 0) "Закладка сегодня" else "День ${metrics.daysElapsed}"
    val subtitle = listOf(batch.rawMaterial.ifBlank { batch.category }, dayLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val caption = if (lastLog != null) "замер " + fmtDateShort(lastLog.atEpochMs)
    else "начальный вес · без замеров"

    // Обложка квадратная, как и файлы фото (1:1).
    val narrow = LocalConfiguration.current.screenWidthDp < 390
    val coverSize = if (narrow) 92.dp else 112.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.card, p.cardEnd)))
            .border(1.dp, if (warning) WarnLine else p.cardLine, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 15.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                val numShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 42.dp)
                        .clip(numShape)
                        .background(p.chip)
                        .border(1.dp, p.chipLine, numShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = batch.number.toString().padStart(2, '0'),
                        color = p.chipIcon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = batch.name,
                        color = p.ink,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        color = p.muted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                BatchCover(
                    batch = batch,
                    photoFile = photoFile,
                    size = coverSize,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 19.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = fmtGrams(metrics.currentWeightGrams),
                            color = p.ink,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.9).sp,
                        )
                        Text(
                            text = "г",
                            color = p.body,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
                        )
                    }
                    Text(
                        text = caption,
                        color = p.kicker,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmtPercentChange(rawLoss),
                        color = p.accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "от начального веса",
                        color = p.kicker,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(metrics.progress.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (warning) WarnFill else p.accent),
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 59.dp)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (warning) Amber else p.accent),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = nextActionText(batch, status),
                    color = if (warning) WarnInk else p.soft,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }

            val buttonShape = RoundedCornerShape(10.dp)
            if (batch.archived) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(buttonShape)
                        .border(1.dp, p.cardLine, buttonShape)
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Журнал", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                val due = status.isDueWeigh
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(buttonShape)
                        .background(if (due) p.accent else p.secondary)
                        .border(1.dp, if (due) p.accent else p.secondaryLine, buttonShape)
                        .clickable(role = Role.Button, onClick = onWeigh)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Взвесить",
                        color = if (due) p.onAccent else p.onSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ───────────────────────── Обложка ─────────────────────────

/**
 * Квадратная обложка карточки. Порядок выбора:
 * 1. исходное фото партии из журнала, если оно есть и файл на месте;
 * 2. заглушка позиции каталога assets/catalog/placeholders/ph_<id>.webp;
 * 3. ph_default.webp.
 * Если ничего не нашлось, остаётся плашка цвета p.chip.
 */
@Composable
fun BatchCover(
    batch: Batch,
    photoFile: ((String) -> File)?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val p = LocalAppPalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(14.dp)
    val reqPx = with(density) { size.roundToPx() }

    val userFile = batch.photoFileName?.let { name -> photoFile?.invoke(name) }
    val placeholder = Catalog.byId(batch.productTypeId)?.photoFile ?: Catalog.DEFAULT_PHOTO_FILE
    val key = (userFile?.let { "u:${it.name}:${it.lastModified()}" } ?: "p:$placeholder") + ":$reqPx"

    val bitmap by produceState<ImageBitmap?>(CoverCache.get(key), key) {
        value = CoverCache.get(key) ?: withContext(Dispatchers.IO) {
            val assets = context.applicationContext.assets
            fun fromAsset(name: String) =
                decodeSampled({ runCatching { assets.open(PLACEHOLDER_DIR + name) }.getOrNull() }, reqPx, reqPx)
            val loaded =
                (userFile?.takeIf { it.isFile }?.let { f ->
                    decodeSampled({ runCatching { f.inputStream() }.getOrNull() }, reqPx, reqPx)
                })
                    ?: fromAsset(placeholder)
                    ?: fromAsset(Catalog.DEFAULT_PHOTO_FILE)
            loaded?.also { CoverCache.put(key, it) }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(p.chip)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val PLACEHOLDER_DIR = "catalog/placeholders/"

private object CoverCache : LruCache<String, ImageBitmap>(48)

/** Читает картинку с уменьшением: сторона выборки не меньше нужного размера. */
private fun decodeSampled(open: () -> InputStream?, reqW: Int, reqH: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = open() ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val pixelsStream = open() ?: return null
    return pixelsStream.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}