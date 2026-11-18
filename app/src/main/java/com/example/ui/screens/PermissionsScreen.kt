package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.ui.DakkViewModel
import com.example.ui.onboarding.AutostartGuide
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.DeviceTweaks

/** Ayarlar → İzinler: her iznin ne işe yaradığı ve tek dokunuşla açılması. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(viewModel: DakkViewModel) {
    val context = LocalContext.current
    BackHandler { viewModel.closeSecondaryScreen() }

    // Ayar ekranından dönünce durumları yenile.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val notifications = remember(tick) { DeviceTweaks.hasNotificationPermission(context) }
    val camera = remember(tick) { DeviceTweaks.hasCameraPermission(context) }
    val microphone = remember(tick) { DeviceTweaks.hasMicrophonePermission(context) }
    val install = remember(tick) { DeviceTweaks.canInstallPackages(context) }
    val battery = remember(tick) { DeviceTweaks.isIgnoringBatteryOptimizations(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) DeviceTweaks.open(context, DeviceTweaks.notificationSettingsIntent(context))
        tick++
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) DeviceTweaks.open(context, DeviceTweaks.appDetailsIntent(context))
        tick++
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) DeviceTweaks.open(context, DeviceTweaks.appDetailsIntent(context))
        tick++
    }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeSecondaryScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = { Text("İzinler", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Dakk yalnızca gerçekten ihtiyaç duyduğu izinleri ister. Hepsi isteğe bağlı; istediğin zaman değiştirebilirsin.",
                color = DakkTextSecondaryDark,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                emoji = "🔔",
                title = "Bildirimler",
                text = "Yeni mesaj gelince haber verir. Mesaj içeriği yalnızca telefonunda çözülür.",
                granted = notifications
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifications) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    DeviceTweaks.open(context, DeviceTweaks.notificationSettingsIntent(context))
                }
            }
            PermissionCard(
                emoji = "📷",
                title = "Kamera",
                text = "Yalnızca arkadaşının QR kodunu tararken kullanılır. Fotoğraf çekmez, kaydetmez.",
                granted = camera
            ) {
                if (!camera) cameraLauncher.launch(Manifest.permission.CAMERA)
                else DeviceTweaks.open(context, DeviceTweaks.appDetailsIntent(context))
            }
            PermissionCard(
                emoji = "🎤",
                title = "Mikrofon",
                text = "Yalnızca sesli mesaj kaydederken, mikrofon düğmesine dokunduğunda kullanılır.",
                granted = microphone
            ) {
                if (!microphone) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else DeviceTweaks.open(context, DeviceTweaks.appDetailsIntent(context))
            }
            PermissionCard(
                emoji = "🛡️",
                title = "Dakk Güncelleyici",
                text = "Yeni sürümleri tek dokunuşla kurar. Yalnızca resmi Dakk imzalı sürümler kabul edilir.",
                granted = install
            ) { DeviceTweaks.open(context, DeviceTweaks.installPermissionIntent(context)) }
            PermissionCard(
                emoji = "🔋",
                title = "Pil optimizasyonu",
                text = "Muaf tutulursa mesaj bildirimleri gecikmeden gelir. Dakk çok az pil kullanır.",
                granted = battery
            ) {
                if (!battery) DeviceTweaks.open(context, DeviceTweaks.batteryOptimizationIntent(context))
                else DeviceTweaks.open(context, DeviceTweaks.appDetailsIntent(context))
            }
            if (DeviceTweaks.isXiaomiFamily()) {
                Spacer(Modifier.height(4.dp))
                AutostartGuide()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(emoji: String, title: String, text: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DakkDarkSurface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DakkYellow.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, color = DakkTextSecondaryDark, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (granted) "Açık ✓" else "Aç",
            color = if (granted) Color(0xFF34C759) else DakkYellow,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
