package com.ethran.notable.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import java.io.File

@Composable
fun PagePreview(modifier: Modifier, pageId: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imgFile = remember(pageId) {
        File(context.filesDir, "pages/previews/thumbs/$pageId")
    }

    BoxWithConstraints(modifier = modifier.then(Modifier.background(Color.LightGray))) {
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val imageRequest = remember(imgFile.absolutePath, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(imgFile.takeIf { it.exists() })
                .size(Size(widthPx, heightPx))
                .crossfade(false)
                .build()
        }
        val painter = rememberAsyncImagePainter(model = imageRequest)

        Image(
            painter = painter,
            contentDescription = "Image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxSize()
        )
    }
}
