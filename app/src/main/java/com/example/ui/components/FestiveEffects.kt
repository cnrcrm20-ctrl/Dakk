package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkOrange
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.Festive
import kotlin.math.sin
import kotlin.random.Random

/** Gönderilebilecek yılbaşı kartları (uygulamanın içinde gelir, internetten indirilmez). */
val FESTIVE_CARDS = intArrayOf(R.drawable.dakk_card_1, R.drawable.dakk_card_2, R.drawable.dakk_card_3, R.drawable.dakk_card_4)

private class Flake(val x: Float, val speed: Float, val radius: Float, val sway: Float, val phase: Float, val alpha: Float)

/** Hafif kar yağışı. Dokunmaları engellemez; yalnızca çizim yapar. */
@Composable
fun SnowOverlay(modifier: Modifier = Modifier, count: Int = 42) {
    val flakes = remember(count) {
        val r = Random(2027)
        List(count) {
            Flake(
                x = r.nextFloat(),
                speed = 0.035f + r.nextFloat() * 0.05f,
                radius = 1.2f + r.nextFloat() * 2.6f,
                sway = 0.008f + r.nextFloat() * 0.02f,
                phase = r.nextFloat() * 6.28f,
                alpha = 0.35f + r.nextFloat() * 0.5f
            )
        }
    }
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = it - start }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time / 1_000_000_000f
        flakes.forEachIndexed { i, f ->
            val y = ((f.phase / 6.28f) + t * f.speed) % 1f
            val x = (f.x + sin(t * 0.8f + f.phase) * f.sway + 1f) % 1f
            drawCircle(
                color = Color.White.copy(alpha = f.alpha),
                radius = f.radius * density * (if (i % 7 == 0) 1.4f else 1f),
                center = Offset(x * size.width, y * (size.height + 20f) - 10f)
            )
        }
    }
}

private class Piece(
    val x: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val color: Color,
    val w: Float,
    val h: Float
)

private val confettiColors = listOf(
    DakkYellow, DakkOrange, Color(0xFFFF4D6D), Color(0xFF4DD4FF), Color(0xFF7CFF8A), Color(0xFFB57CFF), Color.White
)

/**
 * Konfeti patlaması. [trigger] her değiştiğinde (0 hariç) ≈2,8 sn oynar. Dokunmaları engellemez.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, pieces: Int = 90) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val parts = remember(trigger) {
        val r = Random(trigger * 7919 + 13)
        List(pieces) {
            Piece(
                x = 0.1f + r.nextFloat() * 0.8f,
                vx = (r.nextFloat() - 0.5f) * 0.9f,
                vy = -(0.9f + r.nextFloat() * 0.9f),
                spin = (r.nextFloat() - 0.5f) * 1440f,
                color = confettiColors[r.nextInt(confettiColors.size)],
                w = 5f + r.nextFloat() * 5f,
                h = 8f + r.nextFloat() * 8f
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2_800, easing = LinearEasing))
    }
    val p = progress.value
    if (p >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val t = p * 2.8f
        val fade = if (p > 0.75f) (1f - p) / 0.25f else 1f
        parts.forEach { c ->
            // Aşağıdan yukarı fırlatılır, yerçekimiyle düşer.
            val x = (c.x + c.vx * t * 0.35f) * size.width
            val y = size.height * (1.02f + c.vy * t * 0.62f + 0.5f * 0.95f * t * t * 0.62f)
            if (y < -40f || y > size.height + 40f) return@forEach
            val angle = c.spin * t
            val wobble = (sin(t * 9f + c.x * 20f) * 0.5f + 0.5f).coerceAtLeast(0.2f)
            rotate(angle, pivot = Offset(x, y)) {
                drawRect(
                    color = c.color.copy(alpha = fade),
                    topLeft = Offset(x - c.w * density / 2, y - c.h * density * wobble / 2),
                    size = Size(c.w * density, c.h * density * wobble)
                )
            }
        }
    }
}

/** Sohbet listesinin başındaki geri sayım kartı. */
@Composable
fun NewYearCountdownCard(now: Long, onSendCard: (() -> Unit)?, modifier: Modifier = Modifier) {
    val countdown = Festive.countdown(now)
    val year = Festive.newYear(now)
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A1D3F))))
                .border(1.dp, DakkYellow.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        ) {
            SnowOverlay(Modifier.matchParentSize(), count = 14)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(if (countdown == null) "🎉" else "🎄", fontSize = 34.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (countdown == null) {
                        Text("Mutlu Yıllar! $year", color = DakkYellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Yeni yılın kutlu olsun 🥳", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    } else {
                        Text("$year'e kalan", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                        Text(
                            Festive.countdownLabel(countdown),
                            color = DakkYellow,
                            fontSize = if (countdown.days == 0L && countdown.hours == 0L) 22.sp else 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (onSendCard != null) {
                    TextButton(onClick = onSendCard) {
                        Text("Kart gönder", color = DakkYellow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Gece yarısı kutlaması: tam ekran, konfetili. Dokununca kapanır. */
@Composable
fun MidnightCelebration(year: Int, onDismiss: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    var burst by remember { mutableLongStateOf(1L) }
    LaunchedEffect(Unit) {
        repeat(3) {
            kotlinx.coroutines.delay(1_600)
            burst += 1
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6050A18))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        SnowOverlay(count = 60)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Image(
                painter = painterResource(R.drawable.dakk_logo_xmas),
                contentDescription = null,
                modifier = Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(Modifier.height(24.dp))
            Text("Mutlu Yıllar!", color = DakkYellow, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("$year", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Dakk ailesi olarak sağlık, huzur ve bol kahkahalı bir yıl dileriz 🦆",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Text("Kapatmak için dokun", color = DakkTextSecondaryDark, fontSize = 12.sp)
        }
        ConfettiBurst(trigger = burst.toInt(), pieces = 140)
    }
}

/** Yılbaşı kartı seçici (alt sayfa). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestiveCardPicker(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DakkDarkSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Text("🎄 Yılbaşı kartı gönder", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Kart, fotoğraf gibi uçtan uca şifreli gönderilir.",
                color = DakkTextSecondaryDark,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            FESTIVE_CARDS.toList().chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { col, res ->
                        val index = row * 2 + col
                        Image(
                            painter = painterResource(res),
                            contentDescription = "Kart ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1080f / 1350f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(index) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
