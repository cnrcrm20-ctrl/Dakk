package com.example

import android.Manifest
import android.app.Activity.ScreenCaptureCallback
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notification.NotificationHelper
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.components.AnnouncementScreen
import com.example.ui.components.DakkSplashAnimation
import com.example.ui.components.ForceUpdateScreen
import com.example.ui.components.MaintenanceScreen
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FounderPanelScreen
import com.example.ui.screens.ChatInfoScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.GroupInviteDialog
import com.example.ui.screens.NewGroupScreen
import com.example.ui.screens.FoundContactDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DakkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Manifest'teki Theme.Dakk.Launch yalnızca açılış penceresi içindir; uygulama koyu temayla çalışır.
        setTheme(R.style.Theme_Dakk)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Açılış animasyonu yalnızca uygulama normal şekilde açıldığında oynar;
        // bildirimden veya davet bağlantısından açılınca kullanıcıyı bekletmeyiz.
        val playSplash = savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN &&
            intent?.hasExtra(NotificationHelper.EXTRA_OPEN_CHAT_UID) != true
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            var showSplash by rememberSaveable { mutableStateOf(playSplash) }
            DakkTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = DakkDarkBg) {
                        DakkApp(viewModel, dialogsAllowed = !showSplash)
                    }
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(350))
                    ) {
                        DakkSplashAnimation(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Android 14 ve üzerinde, sohbet açıkken alınan ekran görüntüsünü sistem bildirir.
     * Ekran görüntüsü engellenmez; karşı tarafa sohbet içinde küçük bir not düşülür.
     */
    private val screenCaptureCallback: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ScreenCaptureCallback { viewModel.onScreenCaptured() }
        } else null
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            (screenCaptureCallback as? ScreenCaptureCallback)?.let {
                runCatching { registerScreenCaptureCallback(mainExecutor, it) }
            }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            (screenCaptureCallback as? ScreenCaptureCallback)?.let {
                runCatching { unregisterScreenCaptureCallback(it) }
            }
        }
        super.onStop()
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
fun DakkApp(viewModel: DakkViewModel, dialogsAllowed: Boolean = true) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val isFounder by viewModel.isFounder.collectAsStateWithLifecycle()
    val announcement by viewModel.announcement.collectAsStateWithLifecycle()
    val preview by viewModel.previewAnnouncement.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Öncelik: bakım > zorunlu güncelleme > duyuru > güncelleme penceresi
    val maintenance = user != null && config.maintenanceEnabled && !isFounder
    val forceUpdate = user != null && !maintenance && config.requiresUpdate(viewModel.appVersion)

    if (maintenance) {
        MaintenanceScreen(config.maintenanceMessage)
        return
    }
    if (forceUpdate) {
        ForceUpdateScreen(
            message = config.forceUpdateMessage,
            latestVersion = update.release?.version,
            downloading = update.downloading,
            progress = update.progress,
            signatureMismatch = update.signatureMismatch,
            onUpdate = { viewModel.startUpdate() },
            onOpenReleasePage = { viewModel.openReleasesPage() }
        )
        return
    }

    if (user == null) {
        AuthScreen()
    } else {
        if (dialogsAllowed) NotificationPermissionRequest()
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState.depth >= initialState.depth && targetState != Screen.Home) {
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
                is Screen.ChatInfo -> ChatInfoScreen(target.key, viewModel)
                is Screen.NewGroup -> NewGroupScreen(target, viewModel)
                Screen.Assistant -> AssistantScreen(viewModel)
                Screen.FounderPanel -> FounderPanelScreen(viewModel)
            }
        }
        if (dialogsAllowed) FoundContactDialog(viewModel)
        if (dialogsAllowed) GroupInviteDialog(viewModel)
    }

    val pendingAnnouncement = announcement
    if (dialogsAllowed && user != null && pendingAnnouncement != null) {
        AnnouncementScreen(
            announcement = pendingAnnouncement,
            isPreview = preview != null,
            onDone = { viewModel.dismissAnnouncement(pendingAnnouncement) }
        )
        return
    }

    val release = update.release
    if (dialogsAllowed && update.showDialog && release != null) {
        UpdateDialog(
            release = release,
            currentVersion = viewModel.appVersion,
            downloading = update.downloading,
            progress = update.progress,
            signatureMismatch = update.signatureMismatch,
            onUpdate = { viewModel.startUpdate() },
            onOpenReleasePage = { viewModel.openReleasePage() },
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
