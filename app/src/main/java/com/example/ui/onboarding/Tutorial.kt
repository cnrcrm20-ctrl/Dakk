package com.example.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Tanıtım turunun işaret ettiği ekran öğelerinin pencere içindeki konumları. */
object CoachTargets {
    const val ADD_CONTACT = "add_contact"
    const val NEW_GROUP = "new_group"
    const val NEW_CHAT = "new_chat"
    const val TAB_CONTACTS = "tab_contacts"
    const val TAB_SETTINGS = "tab_settings"

    val bounds = mutableStateMapOf<String, Rect>()
}

/** Bu öğeyi tanıtım turunda işaretlenebilir yapar. */
fun Modifier.coachTarget(id: String): Modifier = composed {
    onGloballyPositioned { CoachTargets.bounds[id] = it.boundsInWindow() }
}

private enum class TourKind { CARD, TARGET, DEMO }

private data class TourStep(val kind: TourKind, val emoji: String, val title: String, val text: String, val target: String? = null)

private val TOUR = listOf(
    TourStep(TourKind.CARD, "👋", "30 saniyelik tur", "Dakk'ı hızlıca tanıyalım. Ekrana dokunarak ilerleyebilirsin."),
    TourStep(TourKind.TARGET, "➕", "Kişi ekle", "Arkadaşını QR koduyla, davet bağlantısıyla ya da e-postasıyla ekle.", CoachTargets.ADD_CONTACT),
    TourStep(TourKind.TARGET, "💬", "Yeni sohbet", "Buradan kişilerinden birini seç ve yazmaya başla.", CoachTargets.NEW_CHAT),
    TourStep(TourKind.DEMO, "🔒", "Böyle görünür", "Mesajlar uçtan uca şifreli. Basılı tut: yanıtla, tepki ver, sil. Sarı ✓✓ okundu demek."),
    TourStep(TourKind.TARGET, "👥", "Grup ve anket", "Arkadaşlarınla grup kur, anket aç, birlikte karar verin.", CoachTargets.NEW_GROUP),
    TourStep(TourKind.TARGET, "⚙️", "Ayarlar", "Sohbet yedeği, İzinler ve bu tur burada. İstediğin zaman tekrar izle.", CoachTargets.TAB_SETTINGS),
    TourStep(TourKind.CARD, "🎉", "Hepsi bu kadar!", "İyi sohbetler. Dakk hep cebinde.")
)

private const val STEP_MS = 4500
private val DemoBubbleMine = Color(0xFF3D3410)
private val DemoBubbleTheirs = Color(0xFF1E2028)

/** Yeni kurulumlarda bir kez gösterilen, yaklaşık 30 saniyelik tanıtım turu. */
@Composable
fun TutorialOverlay(onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }
    val step = TOUR[index]
    val last = index == TOUR.lastIndex

    fun next() {
        if (last) onFinish() else index++
    }

    BackHandler { onFinish() }

    LaunchedEffect(index, paused) {
        if (paused) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(STEP_MS, easing = LinearEasing))
        if (!last) index++
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = step.target?.let { CoachTargets.bounds[it] }?.translate(-origin)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "pulse"
    )
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            if (target != null) {
                val pad = 8.dp.toPx()
                val hole = target.inflate(pad)
                val radius = CornerRadius(hole.minDimension / 2f)
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
                val grow = 14.dp.toPx() * pulse
                val ring = hole.inflate(grow)
                drawRoundRect(
                    DakkYellow.copy(alpha = 1f - pulse),
                    ring.topLeft,
                    Size(ring.width, ring.height),
                    CornerRadius(ring.minDimension / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TOUR.indices.forEach { i ->
                        LinearProgressIndicator(
                            progress = { if (i < index) 1f else if (i == index) progress.value else 0f },
                            color = DakkYellow,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onFinish) { Text("Turu geç", color = Color.White) }
            }
        }

        val maxH = with(density) { constraints.maxHeight.toDp() }
        val cardAlignment = when {
            target == null -> Alignment.Center
            with(density) { target.center.y.toDp() } > maxH / 2 -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 72.dp),
            contentAlignment = cardAlignment
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 }).togetherWith(fadeOut(tween(150)))
                },
                label = "tour"
            ) { i ->
                val s = TOUR[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (s.kind == TourKind.DEMO) {
                        DemoChat(key = i)
                        Spacer(Modifier.height(16.dp))
                    }
                    TourCard(s, isLast = i == TOUR.lastIndex, onNext = { next() })
                }
            }
        }
    }
}

@Composable
private fun TourCard(step: TourStep, isLast: Boolean, onNext: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DakkDarkSurface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(step.emoji, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(step.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(step.text, color = DakkTextSecondaryDark, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNext) {
            Text(if (isLast) "Başla" else "İleri", color = DakkYellow, fontWeight = FontWeight.Bold)
        }
    }
}

/** Tanıtım için sahte bir sohbet: mesajlar sırayla belirir (hiçbir şey gönderilmez). */
@Composable
private fun DemoChat(key: Int) {
    var shown by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        for (n in 1..4) {
            delay(650)
            shown = n
        }
    }
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DakkDarkBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(DakkYellow.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) { Text("D", color = DakkYellow, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Dakk Rehber", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("çevrimiçi", color = DakkTextTertiaryDark, fontSize = 12.sp)
            }
            Icon(Icons.Default.Lock, null, tint = DakkTextTertiaryDark, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(10.dp))
        DemoBubble(shown >= 1, mine = false, text = "Selam! Dakk'a hoş geldin 👋")
        DemoBubble(shown >= 2, mine = true, text = "Selam! Burası çok sade 😄", read = shown >= 3)
        DemoBubble(shown >= 3, mine = false, text = "Mesajları yalnızca biz okuyabiliyoruz 🔒", reaction = if (shown >= 4) "❤️" else null)
    }
}

@Composable
private fun DemoBubble(visible: Boolean, mine: Boolean, text: String, read: Boolean = false, reaction: String? = null) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
            Box {
                Row(
                    Modifier
                        .widthIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (mine) DemoBubbleMine else DemoBubbleTheirs)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(text, color = Color.White, fontSize = 14.sp)
                    if (mine) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.DoneAll,
                            null,
                            tint = if (read) DakkYellow else DakkTextTertiaryDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (reaction != null) {
                    Text(
                        reaction,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset { IntOffset(12, 28) }
                            .clip(CircleShape)
                            .background(DakkDarkSurface)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
