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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notification.NotificationHelper
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.FoundContactDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DakkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            DakkTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = DakkDarkBg) {
                    DakkApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setForeground(true)
    }

    override fun onPause() {
        viewModel.setForeground(false)
        super.onPause()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(NotificationHelper.EXTRA_OPEN_CHAT_UID)?.let { uid ->
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_CHAT_UID)
            viewModel.openChatWhenReady(uid)
        }
        intent.data?.toString()?.let { link -> viewModel.handleIncomingLink(link) }
    }
}

@Composable
fun DakkApp(viewModel: DakkViewModel) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (user == null) {
        AuthScreen()
    } else {
        NotificationPermissionRequest()
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState is Screen.Chat) {
                    (slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220)))
                }
            },
            label = "screen"
        ) { target ->
            when (target) {
                Screen.Home -> HomeScreen(viewModel)
                is Screen.Chat -> ChatScreen(target.peerUid, viewModel)
            }
        }
        FoundContactDialog(viewModel)
    }

    val release = update.release
    if (update.showDialog && release != null) {
        UpdateDialog(
            release = release,
            currentVersion = viewModel.appVersion,
            downloading = update.downloading,
            progress = update.progress,
            onUpdate = { viewModel.startUpdate() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }
}

/** Android 13+ için bildirim iznini oturum açıldıktan sonra bir kez ister. */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("dakk_prefs", Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("notification_permission_asked", false)) {
            prefs.edit().putBoolean("notification_permission_asked", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
