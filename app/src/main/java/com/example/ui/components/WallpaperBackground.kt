package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hazır sohbet arka planları: resim dosyası yok, hepsi çizilir (APK büyümez, her ekranda keskin). */
enum class WallpaperStyle(val id: String, val label: String, val light: List<Color>, val dark: List<Color>) {
    NONE("none", "Yok", emptyList(), emptyList()),
    SUNRISE("sunrise", "Gün doğumu", listOf(Color(0xFFFFF3C4), Color(0xFFFFD6B8)), listOf(Color(0xFF2A1F0A), Color(0xFF2B140F))),
    OCEAN("ocean", "Okyanus", listOf(Color(0xFFD9F1FF), Color(0xFFCCE0FF)), listOf(Color(0xFF07202E), Color(0xFF0B1630))),
    FOREST("forest", "Orman", listOf(Color(0xFFDFF5E3), Color(0xFFCDEBD9)), listOf(Color(0xFF0A2116), Color(0xFF0E1C17))),
    NIGHT("night", "Gece", listOf(Color(0xFFE4E1FF), Color(0xFFD5DCF7)), listOf(Color(0xFF14102B), Color(0xFF0A0E1F))),
    CANDY("candy", "Şeker", listOf(Color(0xFFFFE1EE), Color(0xFFEBDDFF)), listOf(Color(0xFF2A0E1E), Color(0xFF1C1030))),
    DOTS("dots", "Dakk noktaları", listOf(Color(0xFFFFF8DC), Color(0xFFFFF8DC)), listOf(Color(0xFF15130A), Color(0xFF15130A)));

    companion object {
        fun of(id: String?): WallpaperStyle = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Sohbet mesaj alanının arkasına çizilir. NONE ise hiçbir şey çizmez. */
@Composable
fun WallpaperBackground(id: String, dark: Boolean, modifier: Modifier = Modifier) {
    val style = WallpaperStyle.of(id)
    if (style == WallpaperStyle.NONE) return
    val colors = if (dark) style.dark else style.light
    Box(modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
        val dot = if (style == WallpaperStyle.DOTS) Color(0xFFFFCC00).copy(alpha = if (dark) 0.18f else 0.35f)
        else Color.White.copy(alpha = if (dark) 0.035f else 0.28f)
        val step = if (style == WallpaperStyle.DOTS) 28.dp else 44.dp
        val radius = if (style == WallpaperStyle.DOTS) 3.dp else 7.dp
        Canvas(Modifier.fillMaxSize()) {
            val s = step.toPx()
            val r = radius.toPx()
            var row = 0
            var y = s / 2
            while (y < size.height + s) {
                var x = if (row % 2 == 0) s / 2 else s
                while (x < size.width + s) {
                    drawCircle(dot, r, Offset(x, y))
                    x += s
                }
                y += s * 0.87f
                row++
            }
        }
    }
}

/** Arka plan seçici. [forAll] true ise seçim tüm sohbetlere uygulanır. */
@Composable
fun WallpaperPickerDialog(
    current: String,
    dark: Boolean,
    onPick: (id: String, forAll: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sohbet arka planı", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WallpaperStyle.entries.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { style ->
                            val selected = style.id == current
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onPick(style.id, false) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 58.dp, height = 84.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            if (selected) 2.5.dp else 1.dp,
                                            if (selected) Color(0xFFFFCC00) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    WallpaperBackground(style.id, dark)
                                    if (style == WallpaperStyle.NONE) Text("—", fontSize = 18.sp)
                                    if (selected) {
                                        Box(
                                            Modifier.align(Alignment.BottomEnd).padding(5.dp).size(16.dp)
                                                .clip(CircleShape).background(Color(0xFFFFCC00)),
                                            contentAlignment = Alignment.Center
                                        ) { Text("✓", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(style.label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current, true) }) { Text("Tüm sohbetlere uygula") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}
