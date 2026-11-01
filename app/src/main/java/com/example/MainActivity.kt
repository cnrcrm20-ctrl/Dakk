package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaling.SignalingPayload
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.components.DakkUpdateDialog
import com.example.ui.screens.*
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DakkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Gelen dakk://invite deep link kontrolü
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
        if (data.scheme == "dakk" && data.host == "invite") {
            val raw = data.getQueryParameter("data")
            if (!raw.isNullOrEmpty()) {
                val payload = SignalingPayload.decodeFromCompactString(raw)
                if (payload != null) {
                    if (payload.type == "OFFER") {
                        viewModel.processRemoteOfferAndCreateAnswer(payload) {
                            Toast.makeText(this, "${payload.alias} kullanıcısından teklif alındı!", Toast.LENGTH_LONG).show()
                            viewModel.navigateTo(Screen.Handshake)
                        }
                    } else if (payload.type == "ANSWER") {
                        viewModel.applyRemoteAnswer(payload)
                        Toast.makeText(this, "${payload.alias} cevabı uygulandı! P2P kuruluyor...", Toast.LENGTH_LONG).show()
                        viewModel.navigateTo(Screen.Home)
                    }
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
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = currentScreen) {
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
