package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Karelerin zemin rengi (#FFFEFE) — tema ile aynı olmalı ki geçişte renk farkı görünmesin. */
val DakkSplashBackground = Color(0xFFFFFEFE)

private const val FRAME_DURATION_MS = 100L

private val FRAMES = intArrayOf(
    R.drawable.dakk_splash_00, R.drawable.dakk_splash_01, R.drawable.dakk_splash_02,
    R.drawable.dakk_splash_03, R.drawable.dakk_splash_04, R.drawable.dakk_splash_05,
    R.drawable.dakk_splash_06, R.drawable.dakk_splash_07, R.drawable.dakk_splash_08,
    R.drawable.dakk_splash_09, R.drawable.dakk_splash_10, R.drawable.dakk_splash_11,
    R.drawable.dakk_splash_12, R.drawable.dakk_splash_13, R.drawable.dakk_splash_14,
    R.drawable.dakk_splash_15, R.drawable.dakk_splash_16, R.drawable.dakk_splash_17,
    R.drawable.dakk_splash_18, R.drawable.dakk_splash_19, R.drawable.dakk_splash_20,
    R.drawable.dakk_splash_21, R.drawable.dakk_splash_22, R.drawable.dakk_splash_23,
    R.drawable.dakk_splash_24, R.drawable.dakk_splash_25, R.drawable.dakk_splash_26,
    R.drawable.dakk_splash_27, R.drawable.dakk_splash_28, R.drawable.dakk_splash_29
)

/**
 * Dakk açılış animasyonu: 30 kare, 100 ms/kare (≈3 sn).
 *
 * - Kareler oynatılmadan önce arka planda çözülür (takılma olmaz).
 * - Ekrana dokunulursa animasyon atlanır.
 * - Sistem ayarlarında animasyonlar kapalıysa yalnızca son kare kısa süre gösterilir.
 */
@Composable
fun DakkSplashAnimation(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinished)
    var frames by remember { mutableStateOf<List<ImageBitmap>?>(null) }
    var index by remember { mutableIntStateOf(0) }

    LightStatusBarWhileVisible()

    LaunchedEffect(Unit) {
        val decoded = withContext(Dispatchers.Default) { decodeFrames(context) }
        if (decoded.isEmpty()) {
            finish()
            return@LaunchedEffect
        }
        frames = decoded
        if (animationsDisabled(context)) {
            index = decoded.lastIndex
            delay(600)
        } else {
            for (i in decoded.indices) {
                index = i
                delay(FRAME_DURATION_MS)
            }
            delay(250) // son karede (👍) kısa bir duraklama
        }
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DakkSplashBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finish() },
        contentAlignment = Alignment.Center
    ) {
        frames?.getOrNull(index)?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = "Dakk",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun decodeFrames(context: Context): List<ImageBitmap> {
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
    return try {
        FRAMES.map { id -> BitmapFactory.decodeResource(context.resources, id, options).asImageBitmap() }
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Beyaz açılış ekranında durum/gezinme çubuğu simgeleri koyu olsun; ekran kapanınca eski haline dönsün. */
@Composable
private fun LightStatusBarWhileVisible() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null && previousStatus != null && previousNav != null) {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNav
            }
        }
    }
}
