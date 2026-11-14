package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ChatMember
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.TypingText

private val TypingBubbleColor = Color(0xFF1E2028)

/**
 * Karşı taraf yazarken sohbetin altında beliren gri baloncuk: üç sarı nokta sırayla zıplar.
 * Baloncuk aşağıdan büyüyerek gelir, yazma bitince küçülerek kaybolur. Grupta yazan kişinin
 * fotoğrafı ve adı da görünür.
 */
@Composable
fun TypingIndicator(typers: List<ChatMember>, isGroup: Boolean, modifier: Modifier = Modifier) {
    // Kaybolurken içerik boşalmasın diye son yazanlar hatırlanır.
    val last = remember { mutableListHolder<ChatMember>() }
    if (typers.isNotEmpty()) last.value = typers
    val shown = typers.ifEmpty { last.value }
    AnimatedVisibility(
        visible = typers.isNotEmpty(),
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(180)) +
            scaleIn(
                initialScale = 0.4f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
            ) + fadeIn(tween(150)),
        exit = scaleOut(targetScale = 0.4f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(160)) +
            fadeOut(tween(160)) + shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val first = shown.firstOrNull()
            if (isGroup && first != null) {
                Avatar(first.name, first.photoUrl, size = 28.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                        .background(TypingBubbleColor)
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    BouncingDots()
                }
                if (isGroup) {
                    TypingText.label(shown.map { it.name }, isGroup = true)?.let {
                        Text(
                            it,
                            color = DakkTextSecondaryDark,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BouncingDots(dotSize: Int = 8, color: Color = DakkYellow) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1_050
                        0f at 0 using FastOutSlowInEasing
                        -6f at 230 using FastOutSlowInEasing
                        0f at 460
                        0f at 1_050
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot$index"
            )
            val alpha by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 0.45f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1_050
                        0.45f at 0
                        1f at 230
                        0.45f at 460
                        0.45f at 1_050
                    },
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "alpha$index"
            )
            Box(
                Modifier
                    .offset(y = offset.dp)
                    .size(dotSize.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
    Spacer(Modifier.height(0.dp))
}

private class ListHolder<T> { var value: List<T> = emptyList() }

private fun <T> mutableListHolder(): ListHolder<T> = ListHolder()
