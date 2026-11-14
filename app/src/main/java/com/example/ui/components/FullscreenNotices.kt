package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.config.Announcement
import com.example.ui.screens.openUrl
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow

/**
 * Kurucu panelinden yayınlanan tam ekran duyuru.
 * Kapatılabilir değilse geri tuşu çalışmaz; kullanıcı ana butona basarak devam eder.
 */
@Composable
fun AnnouncementScreen(announcement: Announcement, isPreview: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { if (announcement.dismissible || isPreview) onDone() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = announcement.dismissible || isPreview,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(DakkDarkBg)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(32.dp))
                if (announcement.imageUrl.startsWith("https://")) {
                    AsyncImage(
                        model = announcement.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painterResource(R.drawable.dakk_logo), null, modifier = Modifier.size(80.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (announcement.title.isNotBlank()) {
                    Text(
                        announcement.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (announcement.body.isNotBlank()) {
                    Text(
                        announcement.body,
                        color = DakkTextSecondaryDark,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(32.dp))
                val hasLink = announcement.buttonUrl.startsWith("https://") || announcement.buttonUrl.startsWith("http://")
                Button(
                    onClick = {
                        if (hasLink) openUrl(context, announcement.buttonUrl)
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(announcement.buttonText.ifBlank { "Tamam" }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                if (hasLink && announcement.dismissible) {
                    TextButton(onClick = onDone) { Text("Daha sonra", color = DakkTextSecondaryDark) }
                }
                if (isPreview) {
                    Spacer(Modifier.height(8.dp))
                    Text("Önizleme — kullanıcılara gösterilmedi", color = DakkYellow, fontSize = 12.sp)
                }
            }
            if (announcement.dismissible || isPreview) {
                IconButton(
                    onClick = onDone,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }
        }
    }
}

/** Kurucu panelinden bakım modu açıldığında uygulamanın yerine gösterilir. */
@Composable
fun MaintenanceScreen(message: String) {
    NoticeLayout(
        icon = { Icon(Icons.Default.Build, null, tint = DakkYellow, modifier = Modifier.size(56.dp)) },
        title = "Kısa bir bakım çalışması",
        body = message.ifBlank { "Dakk'ı daha iyi hale getirmek için çalışıyoruz. Lütfen biraz sonra tekrar deneyin." }
    ) {}
}

/** Bu sürüm artık desteklenmiyorsa uygulamanın yerine gösterilir. */
@Composable
fun ForceUpdateScreen(
    message: String,
    latestVersion: String?,
    downloading: Boolean,
    progress: Int,
    signatureMismatch: Boolean,
    onUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DakkDarkBg)
    ) {
        UpdateGlow()
        UpdateScaffold(
            onClose = null,
            bottom = {
                val openPage = signatureMismatch || latestVersion == null
                ProgressButton(
                    label = when {
                        downloading -> "İndiriliyor… %$progress"
                        openPage -> "İndirme sayfasını aç"
                        else -> "Şimdi güncelle"
                    },
                    downloading = downloading,
                    progress = progress,
                    onClick = { if (openPage) onOpenReleasePage() else onUpdate() }
                )
            }
        ) {
            Spacer(Modifier.height(24.dp))
            GlowingLogo()
            Spacer(Modifier.height(22.dp))
            Pill("GÜNCELLEME GEREKLİ")
            Spacer(Modifier.height(14.dp))
            Text(
                if (latestVersion != null) "Dakk $latestVersion seni bekliyor" else "Dakk'ı güncelle",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    signatureMismatch ->
                        "Bu güncelleme yeni bir imza anahtarıyla yayınlandı. Dakk'ı kaldırıp yeni sürümü indirerek kurun."
                    message.isNotBlank() -> message
                    else -> "Dakk'ın bu sürümü artık desteklenmiyor. Devam etmek için güncelle; mesajların ve kişilerin olduğu gibi kalır."
                },
                color = DakkTextSecondaryDark,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoticeLayout(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actions: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DakkDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(Modifier.height(20.dp))
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, color = DakkTextSecondaryDark, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            actions()
        }
    }
}
