package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImageState
import com.example.data.VoiceAttachment
import com.example.media.VoiceMeta
import com.example.media.VoicePlayback
import com.example.media.Waveform
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkStatusFailed
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow

/** Baloncuk içindeki sesli mesaj: oynat/duraklat, dalga formu (dokun/kaydır ile atla), süre ve hız. */
@Composable
fun VoiceMessageContent(
    voice: VoiceAttachment,
    playback: VoicePlayback,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeed: () -> Unit,
    onRetry: () -> Unit
) {
    val isCurrent = playback.id == voice.mediaId
    val playing = isCurrent && playback.playing
    val progress = playback.progressOf(voice.mediaId)
    val ready = voice.state == ImageState.READY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(250.dp)
            .padding(vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (ready) DakkYellow else DakkDarkSurfaceVariant)
                .clickable(enabled = ready || voice.state == ImageState.FAILED) {
                    if (ready) onToggle() else onRetry()
                }
        ) {
            when (voice.state) {
                ImageState.READY -> Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Duraklat" else "Oynat",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
                ImageState.FAILED -> Icon(Icons.Default.Refresh, "Yeniden dene", tint = DakkYellow)
                ImageState.EXPIRED -> Icon(Icons.Default.HourglassDisabled, "Süresi doldu", tint = DakkTextTertiaryDark)
                else -> CircularProgressIndicator(color = DakkYellow, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            WaveformBars(
                bars = voice.waveform,
                progress = progress,
                enabled = ready,
                onSeek = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        voice.state == ImageState.EXPIRED -> "Süresi doldu"
                        voice.state == ImageState.FAILED -> "İndirilemedi · dokun, tekrar dene"
                        isCurrent && (playing || playback.positionMs > 0) -> VoiceMeta.formatDuration(playback.positionMs)
                        else -> VoiceMeta.formatDuration(voice.durationMs)
                    },
                    color = DakkTextSecondaryDark,
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                if (isCurrent) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onSpeed)
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(speedLabel(playback.speed), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun speedLabel(speed: Float): String = when (speed) {
    1.5f -> "1,5×"
    2f -> "2×"
    else -> "1×"
}

@Composable
private fun WaveformBars(
    bars: List<Int>,
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val values = bars.ifEmpty { List(Waveform.BARS) { 6 } }
    Canvas(
        modifier
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (enabled) detectHorizontalDragGestures { change, _ ->
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val n = values.size
        val slot = size.width / n
        val barWidth = (slot * 0.6f).coerceAtLeast(1.5f)
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / Waveform.MAX).coerceIn(0.08f, 1f) * size.height
            val played = (i + 0.5f) / n <= progress
            drawRoundRect(
                color = if (played) DakkYellow else Color.White.copy(alpha = 0.38f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/** Kayıt sürerken giriş alanının yerine geçen çubuk: kırmızı nabız, süre, iptal ve gönder. */
@Composable
fun VoiceRecordingBar(
    elapsedMs: Long,
    level: Float,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "recPulse"
    )
    val remaining = VoiceMeta.MAX_DURATION_MS - elapsedMs
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Kaydı sil", tint = DakkStatusFailed)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(DakkDarkSurfaceVariant)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(DakkStatusFailed)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                VoiceMeta.formatDuration(elapsedMs.coerceAtLeast(0)),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.08f + 0.92f * level.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DakkYellow)
                )
            }
            if (remaining in 1L..30_000L) {
                Spacer(Modifier.width(8.dp))
                Text("${remaining / 1000} sn", color = DakkStatusFailed, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(DakkYellow)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Sesli mesajı gönder", tint = Color.Black)
        }
    }
}
