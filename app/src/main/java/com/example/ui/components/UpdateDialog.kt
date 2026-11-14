package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import com.example.update.ReleaseInfo
import com.example.util.ReleaseNotes
import java.util.Locale

/**
 * Yeni sürüm ekranı: tam ekran, koyu zemin üzerinde parlayan Dakk logosu, sürüm geçişi,
 * kart halinde "Neler yeni?" listesi ve indirme ilerlemesini içinde gösteren büyük düğme.
 */
@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    downloading: Boolean,
    progress: Int,
    signatureMismatch: Boolean,
    onUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(
            Modifier
                .fillMaxSize()
                .background(DakkDarkBg)
        ) {
            UpdateGlow()
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(250)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 6 },
                exit = fadeOut() + slideOutVertically { it / 6 }
            ) {
                if (signatureMismatch) {
                    ReinstallContent(release, onOpenReleasePage, onDismiss)
                } else {
                    UpdateContent(release, currentVersion, downloading, progress, onUpdate, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun UpdateContent(
    release: ReleaseInfo,
    currentVersion: String,
    downloading: Boolean,
    progress: Int,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val blocks = remember(release.notes) { ReleaseNotes.parse(release.notes) }
    UpdateScaffold(
        onClose = if (downloading) null else onDismiss,
        bottom = {
            ProgressButton(
                label = if (downloading) "İndiriliyor… %$progress" else "Şimdi güncelle",
                downloading = downloading,
                progress = progress,
                onClick = onUpdate
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (downloading) "İndirme bitince kurulum ekranı açılır. Mesajların ve kişilerin olduğu gibi kalır."
                else "Daha sonra",
                color = if (downloading) DakkTextTertiaryDark else DakkTextSecondaryDark,
                fontSize = if (downloading) 12.sp else 15.sp,
                fontWeight = if (downloading) FontWeight.Normal else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !downloading, onClick = onDismiss)
                    .padding(vertical = 10.dp)
            )
        }
    ) {
        GlowingLogo()
        Spacer(Modifier.height(22.dp))
        Pill("YENİ SÜRÜM HAZIR")
        Spacer(Modifier.height(14.dp))
        Text(
            "Dakk ${release.version}",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        VersionTransition(currentVersion, release.version, release.apkSize)
        Spacer(Modifier.height(28.dp))
        if (blocks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = DakkYellow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Neler yeni?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            NotesCard(blocks)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = DakkTextTertiaryDark, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Güncelleme resmi GitHub sürümünden indirilir ve imzası doğrulanır.",
                color = DakkTextTertiaryDark,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ReinstallContent(release: ReleaseInfo, onOpenReleasePage: () -> Unit, onDismiss: () -> Unit) {
    UpdateScaffold(
        onClose = onDismiss,
        bottom = {
            ProgressButton(label = "APK'yı indir", downloading = false, progress = 0, onClick = onOpenReleasePage)
        }
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(DakkYellow.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.WarningAmber, null, tint = DakkYellow, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Yeniden kurulum gerekiyor", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Dakk v${release.version}, güvenlik için yeni bir imza anahtarıyla yayınlandı. " +
                "Android bu nedenle güncellemeyi mevcut uygulamanın üzerine kuramıyor.",
            color = DakkTextSecondaryDark,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        listOf(
            "Aşağıdaki düğmeyle yeni sürümün APK dosyasını indir.",
            "Mevcut Dakk uygulamasını kaldır.",
            "İndirdiğin APK'yı açıp kur ve Google ile tekrar giriş yap."
        ).forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DakkDarkSurface)
                    .border(1.dp, DakkDarkBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(DakkYellow),
                    contentAlignment = Alignment.Center
                ) { Text("${index + 1}", color = Color.Black, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(12.dp))
                Text(step, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Bu işlem yalnızca bir kez gereklidir. Kişilerin hesabında saklı kalır; şifreleme anahtarları yenileneceği için eski mesajlar okunamaz.",
            color = DakkTextTertiaryDark,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Kaydırılabilir içerik + altta sabit düğme alanı + sağ üstte kapatma. */
@Composable
internal fun UpdateScaffold(
    onClose: (() -> Unit)?,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = DakkTextSecondaryDark)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, DakkDarkBg.copy(alpha = 0.95f), DakkDarkBg)))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            content = bottom
        )
    }
}

/** Ekranın üstünde yumuşak sarı ışıma. */
@Composable
internal fun BoxScope.UpdateGlow() {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(420.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(DakkYellow.copy(alpha = 0.22f), DakkYellow.copy(alpha = 0.06f), Color.Transparent),
                    radius = 900f
                )
            )
    )
}

/** Hafifçe nefes alan halkalar içinde Dakk logosu. */
@Composable
internal fun GlowingLogo() {
    val transition = rememberInfiniteTransition(label = "logo")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        Box(
            Modifier
                .size(150.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(DakkYellow.copy(alpha = 0.08f))
        )
        Box(
            Modifier
                .size(118.dp)
                .scale(2f - pulse)
                .clip(CircleShape)
                .background(DakkYellow.copy(alpha = 0.12f))
        )
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, DakkYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(R.drawable.dakk_logo), contentDescription = "Dakk", modifier = Modifier.size(72.dp))
        }
    }
}

@Composable
internal fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(DakkYellow.copy(alpha = 0.14f))
            .border(1.dp, DakkYellow.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, color = DakkYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun VersionTransition(current: String, latest: String, apkSize: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VersionChip("v$current", highlighted = false)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            null,
            tint = DakkTextTertiaryDark,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(16.dp)
        )
        VersionChip("v$latest", highlighted = true)
        if (apkSize > 0) {
            Spacer(Modifier.width(10.dp))
            Text(formatSize(apkSize), color = DakkTextTertiaryDark, fontSize = 13.sp)
        }
    }
}

@Composable
private fun VersionChip(text: String, highlighted: Boolean) {
    Text(
        text,
        color = if (highlighted) Color.Black else DakkTextSecondaryDark,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) DakkYellow else DakkDarkSurface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun NotesCard(blocks: List<ReleaseNotes.Block>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DakkDarkSurface)
            .border(1.dp, DakkDarkBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNotes.Block.Heading -> Text(
                    block.text,
                    color = DakkYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                is ReleaseNotes.Block.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(DakkYellow)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(block.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                }
                is ReleaseNotes.Block.Paragraph -> Text(block.text, color = DakkTextSecondaryDark, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** İndirme ilerlemesini kendi içinde dolarak gösteren büyük sarı düğme. */
@Composable
internal fun ProgressButton(label: String, downloading: Boolean, progress: Int, onClick: () -> Unit) {
    val fill by animateFloatAsState(
        targetValue = if (downloading) (progress.coerceIn(0, 100) / 100f) else 1f,
        animationSpec = tween(300),
        label = "fill"
    )
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(if (downloading) DakkDarkSurface else DakkYellow)
            .border(1.dp, if (downloading) DakkYellow.copy(alpha = 0.5f) else DakkYellow, shape)
            .clickable(enabled = !downloading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (downloading) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .background(Brush.horizontalGradient(listOf(DakkYellow.copy(alpha = 0.7f), DakkYellow)))
            )
        }
        Text(
            label,
            color = if (downloading && fill < 0.5f) Color.White else Color.Black,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.forLanguageTag("tr-TR"), "%.1f MB", mb)
}
