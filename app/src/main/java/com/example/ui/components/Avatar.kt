package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

private val avatarPalette = listOf(
    Color(0xFFFF8A00), Color(0xFF7C4DFF), Color(0xFF00A884), Color(0xFF1E88E5),
    Color(0xFFE91E63), Color(0xFF43A047), Color(0xFFF4511E), Color(0xFF5E35B1)
)

@Composable
fun Avatar(
    name: String,
    photoUrl: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) {
        name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
    }
    val bg = remember(name) { avatarPalette[(name.hashCode() and 0x7FFFFFFF) % avatarPalette.size] }

    val fallback: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
    }

    Box(modifier = modifier.size(size).clip(CircleShape)) {
        if (photoUrl.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
                loading = { fallback() },
                error = { fallback() }
            )
        }
    }
}
