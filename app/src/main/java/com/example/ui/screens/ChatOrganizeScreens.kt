package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatOrganizer
import com.example.data.ChatSummary
import com.example.ui.DakkViewModel
import com.example.ui.components.Avatar
import com.example.ui.components.rememberBiometricGate
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow

// ---------------------------------------------------------------------------------------------- Sohbet işlemleri

/** Sohbete basılı tutunca açılan sayfa: sabitle, arşivle, gizle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatActionsSheet(
    chat: ChatSummary,
    pinned: Boolean,
    archived: Boolean,
    hidden: Boolean,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = DakkDarkSurface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Avatar(chat.peerName, chat.peerPhotoUrl, size = 44.dp, model = chat.photoModel, isGroup = chat.isGroup)
                Spacer(Modifier.width(12.dp))
                Text(
                    chat.peerName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!hidden) {
                ActionRow(
                    icon = if (pinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                    title = if (pinned) "Sabitlemeyi kaldır" else "Üste sabitle",
                    subtitle = if (pinned) null else "En fazla ${ChatOrganizer.MAX_PINNED} sohbet"
                ) { onPin(); onDismiss() }
                ActionRow(
                    icon = if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
                    title = if (archived) "Arşivden çıkar" else "Arşivle",
                    subtitle = if (archived) null else "Yeni mesaj gelse de arşivde kalır"
                ) { onArchive(); onDismiss() }
            }
            ActionRow(
                icon = if (hidden) Icons.Default.LockOpen else Icons.Default.Fingerprint,
                title = if (hidden) "Gizli sohbetten çıkar" else "Gizli sohbete taşı",
                subtitle = if (hidden) "Sohbet listesine geri döner" else "Parmak izinle açılır, bildirimde içerik görünmez",
                accent = !hidden
            ) { onHide(); onDismiss() }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (accent) DakkYellow.copy(alpha = 0.16f) else DakkDarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (accent) DakkYellow else Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = DakkTextSecondaryDark, fontSize = 13.sp)
        }
    }
}

/** Ana listenin en üstündeki "Arşiv" satırı. */
@Composable
internal fun ArchivedEntryRow(count: Int, unread: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Archive, null, tint = DakkYellow, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text("Arşiv", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            if (unread > 0) "$unread yeni" else "$count",
            color = if (unread > 0) DakkYellow else DakkTextTertiaryDark,
            fontSize = 13.sp,
            fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ---------------------------------------------------------------------------------------------- Arşiv

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedChatsScreen(viewModel: DakkViewModel) {
    val lists by viewModel.chatLists.collectAsStateWithLifecycle()
    val organizer by viewModel.organizer.collectAsStateWithLifecycle()
    val typing by viewModel.typingLabels.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<ChatSummary?>(null) }
    val biometric = rememberBiometricGate()
    BackHandler { viewModel.closeSecondaryScreen() }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeSecondaryScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = { Text("Arşiv", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        }
    ) { padding ->
        if (lists.archived.isEmpty()) {
            EmptyHint(
                Modifier.padding(padding),
                icon = Icons.Default.Archive,
                title = "Arşiv boş",
                text = "Bir sohbete basılı tutup \"Arşivle\" dediğinde buraya taşınır."
            )
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "hint") {
                Text(
                    "Arşivdeki sohbetler yeni mesaj gelse de burada kalır.",
                    color = DakkTextTertiaryDark,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
            items(lists.archived, key = { it.chatId }) { chat ->
                ChatRow(
                    chat,
                    query = "",
                    typing = typing[chat.peerUid],
                    onClick = { viewModel.openChat(chat.peerUid) },
                    onLongPress = { actionsFor = chat }
                )
            }
        }
    }

    actionsFor?.let { chat ->
        ChatActionsSheet(
            chat = chat,
            pinned = organizer.isPinned(chat.chatId),
            archived = true,
            hidden = false,
            onPin = { viewModel.togglePinChat(chat.chatId) },
            onArchive = { viewModel.toggleArchiveChat(chat.chatId) },
            onHide = { biometric.authenticate("Gizli sohbete taşı") { viewModel.hideChat(chat.chatId, chat.peerUid) } },
            onDismiss = { actionsFor = null }
        )
    }
}

// ---------------------------------------------------------------------------------------------- Gizli sohbetler

/**
 * Gizli sohbetler: kilitliyken parmak izi ekranı, açıkken gizli sohbet listesi. Kilit, bu ekrandan geri çıkınca ya da
 * uygulama bir dakikadan uzun arka planda kalınca yeniden kapanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenChatsScreen(viewModel: DakkViewModel) {
    val unlocked by viewModel.vaultUnlocked.collectAsStateWithLifecycle()
    val lists by viewModel.chatLists.collectAsStateWithLifecycle()
    val typing by viewModel.typingLabels.collectAsStateWithLifecycle()
    val biometric = rememberBiometricGate()
    var actionsFor by remember { mutableStateOf<ChatSummary?>(null) }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    val unlock: () -> Unit = { biometric.authenticate("Gizli sohbetler") { viewModel.onVaultUnlocked() } }

    BackHandler { viewModel.lockVault() }
    LaunchedEffect(unlocked) {
        if (!unlocked && !autoPrompted) {
            autoPrompted = true
            unlock()
        }
    }

    AnimatedContent(
        targetState = unlocked,
        transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
        label = "vault"
    ) { isOpen ->
        if (isOpen) {
            VaultOpen(viewModel, lists.hidden, typing, onLongPress = { actionsFor = it })
        } else {
            VaultLocked(onUnlock = { unlock() }, onBack = { viewModel.lockVault() })
        }
    }

    actionsFor?.let { chat ->
        ChatActionsSheet(
            chat = chat,
            pinned = false,
            archived = false,
            hidden = true,
            onPin = {},
            onArchive = {},
            onHide = { viewModel.unhideChat(chat.chatId) },
            onDismiss = { actionsFor = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultOpen(
    viewModel: DakkViewModel,
    hidden: List<ChatSummary>,
    typing: Map<String, String>,
    onLongPress: (ChatSummary) -> Unit
) {
    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.lockVault() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, null, tint = DakkYellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gizli sohbetler", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.lockVault() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Şimdi kilitle")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "info") { VaultInfoCard() }
            if (hidden.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        Modifier.padding(top = 24.dp),
                        icon = Icons.Default.VisibilityOff,
                        title = "Henüz gizli sohbet yok",
                        text = "Bir sohbete basılı tut ya da sohbet menüsünden \"Gizli sohbete taşı\"yı seç."
                    )
                }
            }
            items(hidden, key = { it.chatId }) { chat ->
                ChatRow(
                    chat,
                    query = "",
                    typing = typing[chat.peerUid],
                    onClick = { viewModel.openChat(chat.peerUid) },
                    onLongPress = { onLongPress(chat) }
                )
            }
        }
    }
}

@Composable
private fun VaultLocked(onUnlock: () -> Unit, onBack: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF16140A), DakkDarkBg, DakkDarkBg)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).clickable(onClick = onUnlock)) {
                // Dışa doğru yayılan halka
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(1f + ring * 0.6f)
                        .alpha((1f - ring) * 0.5f)
                        .border(2.dp, DakkYellow, CircleShape)
                )
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(DakkYellow.copy(alpha = 0.28f), DakkYellow.copy(alpha = 0.06f))))
                        .border(1.dp, DakkYellow.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Kilidi aç", tint = DakkYellow, modifier = Modifier.size(64.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Gizli sohbetler", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Yalnızca sen açabilirsin. Parmak izine dokun ya da ekran kilidini kullan.",
                color = DakkTextSecondaryDark,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kilidi aç", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
        Text(
            "🔒 Uçtan uca şifreli · Yalnızca bu telefonda",
            color = DakkTextTertiaryDark,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun VaultInfoCard() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(DakkYellow.copy(alpha = 0.14f), DakkDarkSurface)))
            .border(1.dp, DakkDarkBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Icon(Icons.Default.Fingerprint, null, tint = DakkYellow, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Burası yalnızca senin", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Gizli sohbetler listede ve aramada görünmez, bildirimlerinde kişi adı ya da mesaj yazmaz. " +
                    "Yalnızca bu telefonda geçerlidir; karşı taraf bunu görmez.",
                color = DakkTextSecondaryDark,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier, icon: ImageVector, title: String, text: String) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(DakkDarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = DakkTextSecondaryDark, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = DakkTextSecondaryDark, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}
