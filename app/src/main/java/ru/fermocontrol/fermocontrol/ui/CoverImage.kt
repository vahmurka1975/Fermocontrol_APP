package ru.fermocontrol.fermocontrol.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.fermocontrol.fermocontrol.data.CoverLoader
import ru.fermocontrol.fermocontrol.model.Batch

/** Обложка партии. Размер задаёт вызывающий код, картинка обрезается по центру. */
@Composable
fun CoverImage(
    batch: Batch,
    modifier: Modifier = Modifier,
    maxSidePx: Int = 400,
) {
    val loader = CoverLoader.get(LocalContext.current)
    val key = loader.keyFor(batch)

    val bitmap by produceState<Bitmap?>(
        initialValue = loader.cached(batch, maxSidePx),
        key, maxSidePx,
    ) {
        value = loader.load(batch, maxSidePx)
    }

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B222B)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}