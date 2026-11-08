package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.ImageAttachment
import com.example.data.ImageState
import com.example.ui.theme.DakkYellow
import java.io.File

/** Mesaj balonu içindeki fotoğraf. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageMessageContent(
    image: ImageAttachment,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val ratio = if (image.width > 0 && image.height > 0) {
        (image.width.toFloat() / image.height).coerceIn(0.6f, 1.8f)
    } else 1f
    val thumb = rememberThumb(image.thumbBase64)

    Box(
        Modifier
            .width(240.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2C34)),
        contentAlignment = Alignment.Center
    ) {
        if (image.state == ImageState.READY && image.localPath != null) {
            AsyncImage(
                model = File(image.localPath),
                contentDescription = "Fotoğraf",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            )
        } else {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(12.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                when (image.state) {
                    ImageState.UPLOADING, ImageState.DOWNLOADING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = DakkYellow, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (image.state == ImageState.UPLOADING) "Gönderiliyor…" else "İndiriliyor…",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    ImageState.EXPIRED -> StatusLabel(Icons.Default.HourglassDisabled, "Fotoğrafın süresi doldu")
                    ImageState.FAILED -> Box(Modifier.clickable(onClick = onRetry).padding(12.dp)) {
                        StatusLabel(Icons.Default.Refresh, "Yüklenemedi\nTekrar denemek için dokunun")
                    }
                    ImageState.READY -> StatusLabel(Icons.Default.BrokenImage, "Fotoğraf bulunamadı")
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun rememberThumb(base64: String?): ImageBitmap? = remember(base64) {
    if (base64.isNullOrBlank()) null else runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** Tam ekran fotoğraf görüntüleyici (iki parmakla yakınlaştırma). */
@Composable
fun ImageViewerDialog(file: File, onSave: () -> Unit, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Fotoğraf",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Kapat", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave) { Icon(Icons.Default.Download, "Galeriye kaydet", tint = Color.White) }
            }
        }
    }
}
