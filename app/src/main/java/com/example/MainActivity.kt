package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaling.SignalingPayload
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.components.DakkUpdateDialog
import com.example.ui.components.IncomingFriendRequestDialog
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.screens.*
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DakkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Gelen dakk://invite veya https://cnrcrm20-ctrl.github.io/Dakk/invite deep link kontrolü
        handleIncomingDeepLink(intent)

        setContent {
            DakkTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DakkDarkBg
                ) {
                    DakkApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val isDakkCustomScheme = data.scheme == "dakk" && data.host == "invite"
        val isGithubPagesScheme = data.scheme == "https" &&
                data.host == "cnrcrm20-ctrl.github.io" &&
                (data.path?.contains("/invite") == true || data.path?.contains("/Dakk") == true)

        if (isDakkCustomScheme || isGithubPagesScheme) {
            val raw = data.getQueryParameter("data")
            if (!raw.isNullOrEmpty()) {
                val payload = SignalingPayload.decodeFromCompactString(raw)
                if (payload != null) {
                    viewModel.onIncomingInviteReceived(payload)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.webRtc.closePeerConnection()
    }
}

@Composable
fun DakkApp(viewModel: DakkViewModel) {
    val context = LocalContext.current
    var isPrivacyAccepted by remember {
        val prefs = context.getSharedPreferences("dakk_prefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("is_privacy_accepted", false))
    }

    // Gerekli tüm izinleri (Mikrofon, Bildirim, Kamera) tek seferde ve kesintisiz talep et
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // İzinler işlendi
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingIncomingInvite.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isPrivacyAccepted) {
            PrivacyPolicyDialog(
                viewModel = viewModel,
                onAccepted = { isPrivacyAccepted = true }
            )
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState is Screen.ChatDetail) {
                    (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn()).togetherWith(
                        slideOutHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeOut()
                    )
                } else {
                    (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn()).togetherWith(
                        slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut()
                    )
                }
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                is Screen.Splash -> {
                    SplashScreen(
                        onTimeout = { viewModel.navigateTo(Screen.Home) }
                    )
                }
                is Screen.Home -> {
                    HomeScreen(viewModel = viewModel)
                }
                is Screen.ChatDetail -> {
                    ChatScreen(
                        peer = screen.peer,
                        viewModel = viewModel
                    )
                }
                is Screen.Handshake -> {
                    HandshakeScreen(viewModel = viewModel)
                }
                is Screen.Diagnostic -> {
                    NetworkDiagnosticScreen(viewModel = viewModel)
                }
                is Screen.Profile -> {
                    ProfileScreen(viewModel = viewModel)
                }
            }
        }

        // Gelen Arkadaşlık / El Sıkışma İsteği Modal Diyaloğu
        pendingInvite?.let { invitePayload ->
            IncomingFriendRequestDialog(
                payload = invitePayload,
                onAccept = {
                    viewModel.acceptIncomingInvite(invitePayload)
                },
                onDismiss = {
                    viewModel.dismissIncomingInvite()
                }
            )
        }

        // GitHub Release Güncelleme Bildirimi
        if (showUpdateDialog && updateInfo != null) {
            DakkUpdateDialog(
                releaseInfo = updateInfo!!,
                downloadProgress = downloadProgress,
                isDownloading = isDownloading,
                onDownloadClick = { viewModel.downloadAndInstallUpdate() },
                onDismissClick = { viewModel.dismissUpdateDialog() }
            )
        }
    }
}
