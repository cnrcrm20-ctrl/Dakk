package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import com.example.data.ChatIds
import com.example.ui.components.CreatePollDialog
import com.example.ui.components.PollContent
import com.example.ui.components.TypingIndicator
import com.example.util.MuteOptions
import com.example.util.TypingText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MessageStatus
import com.example.data.ReplyQuote
import com.example.data.UiMessage
import com.example.ui.DakkViewModel
import com.example.ui.components.AiConsentDialog
import com.example.ui.components.Avatar
import com.example.ui.components.ImageMessageContent
import com.example.ui.components.ImageViewerDialog
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkStatusFailed
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.Reactions
import com.example.util.ReplySnippets
import com.example.util.ScreenshotNotice
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val BubbleMine = Color(0xFF3D3410)
private val BubbleTheirs = Color(0xFF1E2028)
private val BubbleAssistant = Color(0xFF26233A)
private val turkish: Locale = Locale.forLanguageTag("tr-TR")

/** Grup sohbetinde gönderen adlarının renkleri (koyu temada okunaklı tonlar). */
private val senderColors = listOf(
    Color(0xFFFFB74D), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFF06292),
    Color(0xFFBA68C8), Color(0xFF4DD0E1), Color(0xFFFF8A65), Color(0xFFAED581)
)

private fun senderColor(uid: String): Color = senderColors[(uid.hashCode() and 0x7FFFFFFF) % senderColors.size]

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(peerUid: String, viewModel: DakkViewModel) {
    val context = LocalContext.current
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val assistantBusyFor by viewModel.chatAssistantBusy.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val typers by viewModel.activeTyping.collectAsStateWithLifecycle()
    val searchRequest by viewModel.chatSearchRequest.collectAsStateWithLifecycle()

    val chat = activeChat?.takeIf { it.key == peerUid }
    val isGroup = ChatIds.isGroup(peerUid)
    val peer = chat?.peer
    val contact = contacts.firstOrNull { it.uid == peerUid }
    val title = chat?.title ?: contact?.alias ?: if (isGroup) "Grup" else "Dakk Kullanıcısı"
    val isContact = contact != null
    val canSend = chat?.canSend == true
    val isMember = chat?.isMember != false
    fun nameOf(uid: String): String = chat?.member(uid)?.name ?: if (uid == peerUid) title else "Dakk Kullanıcısı"

    var draft by rememberSaveable(peerUid) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<UiMessage?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var pendingMention by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember(peerUid) { mutableStateOf<UiMessage?>(null) }
    var creatingPoll by remember { mutableStateOf(false) }
    var searching by rememberSaveable(peerUid) { mutableStateOf(false) }
    var query by rememberSaveable(peerUid) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val myUid = viewModel.currentUid

    LaunchedEffect(searchRequest) {
        if (searchRequest == peerUid) {
            searching = true
            viewModel.consumeChatSearchRequest()
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.sendImage(peerUid, uri)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = viewingImage
        if (granted && id != null) viewModel.saveImageToGallery(id)
    }

    fun submit() {
        val text = draft
        if (viewModel.isAssistantMention(text) && config.assistantEnabled && !viewModel.hasAiConsent("chat_$peerUid")) {
            pendingMention = text
        } else if (viewModel.sendMessage(peerUid, text, replyingTo)) {
            draft = ""
            replyingTo = null
        }
    }

    val myUploads = uploads[peerUid].orEmpty()
    val allMessages = remember(messages, myUploads) { messages + myUploads }
    val visibleMessages = remember(allMessages, searching, query) {
        val q = query.trim()
        if (!searching || q.isEmpty()) allMessages
        else allMessages.filter { m ->
            m.systemText == null && !m.isScreenshotNotice &&
                ((m.text?.contains(q, ignoreCase = true) == true) ||
                    (m.poll?.options?.any { it.contains(q, ignoreCase = true) } == true))
        }
    }
    val assistantTyping = assistantBusyFor == peerUid
    val listState = rememberLazyListState()
    val chatItems = remember(visibleMessages) { buildChatItems(visibleMessages) }
    val typingLabel = TypingText.label(typers.map { it.name }, isGroup)

    BackHandler {
        when {
            searching -> { searching = false; query = "" }
            else -> viewModel.closeChat()
        }
    }

    val showUnknownBanner = !isGroup && !isContact && peer != null && messages.any { !it.isFromMe }
    val headerCount = if (showUnknownBanner) 2 else 1
    LaunchedEffect(chatItems.size, assistantTyping, typers.isNotEmpty()) {
        val extra = (if (assistantTyping) 1 else 0) + 1
        if (chatItems.isNotEmpty() && !(searching && query.isNotBlank())) {
            listState.animateScrollToItem(headerCount + chatItems.size - 1 + extra)
        }
    }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            if (searching) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Aramayı kapat")
                        }
                    },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it.take(100) },
                            placeholder = { Text("Sohbette ara") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = DakkTextSecondaryDark) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Temizle") }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = DakkYellow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
                )
            } else TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeChat() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.openChatInfo(peerUid) }
                    ) {
                        Avatar(title, null, size = 40.dp, model = chat?.photoModel, isGroup = isGroup)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (chat != null && MuteOptions.isMuted(chat.muteUntil, System.currentTimeMillis())) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.NotificationsOff, "Sessizde", tint = DakkTextSecondaryDark, modifier = Modifier.size(14.dp))
                                }
                            }
                            AnimatedContent(
                                targetState = typingLabel,
                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                label = "subtitle"
                            ) { label ->
                                if (label != null) {
                                    Text(label, fontSize = 12.sp, color = DakkYellow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else Row(verticalAlignment = Alignment.CenterVertically) {
                                    if ((chat?.disappearSeconds ?: 0L) > 0) {
                                        Icon(Icons.Default.Timer, null, tint = DakkYellow, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                    } else {
                                        Icon(Icons.Default.Lock, null, tint = DakkYellow, modifier = Modifier.size(11.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        if (isGroup) "${chat?.members?.size ?: 0} kişi · dokun, bilgileri gör" else "Uçtan uca şifreli",
                                        fontSize = 12.sp,
                                        color = DakkTextSecondaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Diğer")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isGroup) "Grup bilgileri" else "Sohbet bilgileri") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; viewModel.openChatInfo(peerUid) }
                        )
                        DropdownMenuItem(
                            text = { Text("Sohbette ara") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            onClick = { menuOpen = false; searching = true }
                        )
                        if (isGroup && isMember) {
                            DropdownMenuItem(
                                text = { Text("Anket oluştur") },
                                leadingIcon = { Icon(Icons.Default.Poll, null) },
                                onClick = { menuOpen = false; creatingPoll = true }
                            )
                        }
                        if (!isGroup) {
                            if (isContact) {
                                DropdownMenuItem(
                                    text = { Text("Kişilerden çıkar") },
                                    leadingIcon = { Icon(Icons.Default.PersonRemove, null) },
                                    onClick = { menuOpen = false; viewModel.removeContact(peerUid) }
                                )
                            } else if (peer != null) {
                                DropdownMenuItem(
                                    text = { Text("Kişilere ekle") },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                    onClick = { menuOpen = false; viewModel.confirmAddContact(peer) }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Sohbeti temizle") },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                            onClick = { menuOpen = false; confirmClear = true }
                        )
                        if (!isGroup) {
                            DropdownMenuItem(
                                text = { Text("Engelle", color = DakkStatusFailed) },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = DakkStatusFailed) },
                                onClick = { menuOpen = false; confirmBlock = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .background(DakkDarkBg)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (isGroup && chat != null && !isMember) {
                    Text(
                        "Bu grubun üyesi değilsiniz. Mesaj gönderemezsiniz.",
                        color = DakkTextSecondaryDark,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                    return@Column
                }
                if (!isGroup && !canSend && peer != null) {
                    Text(
                        "$title henüz Dakk'ın güncel sürümüne giriş yapmadı. Giriş yaptığında mesaj gönderebileceksiniz.",
                        color = DakkTextSecondaryDark,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                replyingTo?.let { target ->
                    ReplyComposerBar(
                        authorLabel = if (target.isFromMe) "Sen" else nameOf(target.senderUid),
                        text = ReplySnippets.of(target.poll?.let { "📊 ${it.question}" } ?: target.text, target.image != null),
                        onCancel = { replyingTo = null }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (config.imagesEnabled) {
                        IconButton(
                            onClick = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = canSend,
                            modifier = Modifier.size(if (isGroup) 44.dp else 52.dp)
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Fotoğraf gönder",
                                tint = if (canSend) DakkYellow else DakkTextTertiaryDark
                            )
                        }
                    }
                    if (isGroup) {
                        IconButton(
                            onClick = { creatingPoll = true },
                            enabled = canSend,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.Poll,
                                contentDescription = "Anket oluştur",
                                tint = if (canSend) DakkYellow else DakkTextTertiaryDark
                            )
                        }
                    }
                    TextField(
                        value = draft,
                        onValueChange = {
                            if (it.length <= DakkViewModel.MAX_MESSAGE_CHARS) {
                                draft = it
                                viewModel.onDraftChanged(peerUid, it)
                            }
                        },
                        placeholder = { Text(if (config.assistantEnabled) "Mesaj (asistan için @dakk)" else "Mesaj") },
                        enabled = canSend,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DakkDarkSurfaceVariant,
                            unfocusedContainerColor = DakkDarkSurfaceVariant,
                            disabledContainerColor = DakkDarkSurfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    val sendEnabled = canSend && draft.isNotBlank()
                    IconButton(
                        onClick = { submit() },
                        enabled = sendEnabled,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (sendEnabled) DakkYellow else DakkDarkSurfaceVariant)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = if (sendEnabled) Color.Black else DakkTextTertiaryDark
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "e2ee_notice") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        color = DakkYellow.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
                    ) {
                        Text(
                            if (searching && query.isNotBlank()) {
                                if (visibleMessages.isEmpty()) "🔍 \"${query.trim()}\" için sonuç yok" else "🔍 ${visibleMessages.size} sonuç"
                            } else "🔒 Mesajlar uçtan uca şifrelenir. Dakk dahil hiç kimse içeriğini okuyamaz.",
                            color = DakkYellow,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            if (showUnknownBanner && peer != null) {
                item(key = "unknown_sender") {
                    Surface(
                        color = DakkDarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Bu kişi kişileriniz arasında değil.", color = Color.White, fontSize = 14.sp)
                            Row {
                                TextButton(onClick = { viewModel.confirmAddContact(peer) }) { Text("Kişilere ekle", color = DakkYellow) }
                                TextButton(onClick = { confirmBlock = true }) { Text("Engelle", color = DakkStatusFailed) }
                            }
                        }
                    }
                }
            }
            itemsIndexed(chatItems, key = { _, it -> it.key }) { index, item ->
                when (item) {
                    is ChatItem.DateHeader -> DateChip(item.label)
                    is ChatItem.Message -> {
                        val m = item.message
                        when {
                            m.systemText != null -> SystemNoteRow(m.systemText)
                            m.isScreenshotNotice -> ScreenshotNoticeRow(ScreenshotNotice.label(m.isFromMe, nameOf(m.senderUid)))
                            else -> {
                                val previous = (chatItems.getOrNull(index - 1) as? ChatItem.Message)?.message
                                val firstOfRun = previous == null || previous.senderUid != m.senderUid ||
                                    previous.systemText != null || previous.isScreenshotNotice
                                MessageBubble(
                                    message = m,
                                    senderLabel = if (isGroup && !m.isFromMe && !m.isAssistant && firstOfRun) nameOf(m.senderUid) else null,
                                    nameOf = ::nameOf,
                                    myUid = myUid,
                                    canInteract = canSend,
                                    onLongPress = { if (!m.id.startsWith("upload_")) selected = m },
                                    onOpenImage = { id -> viewingImage = id },
                                    onRetryImage = { id -> viewModel.retryImage(peerUid, id) },
                                    onReact = { emoji -> viewModel.react(peerUid, m.id, emoji) },
                                    onVote = { option -> viewModel.vote(peerUid, m.id, option) },
                                    onBuildTeams = { viewModel.buildTeams(peerUid, m.id) },
                                    onOpenQuote = { quoted ->
                                        val target = chatItems.indexOfFirst { it.key == quoted.messageId }
                                        if (target >= 0) scope.launch { listState.animateScrollToItem(headerCount + target) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (assistantTyping) {
                item(key = "assistant_typing") { AssistantTypingRow() }
            }
            item(key = "typing_bubble") {
                TypingIndicator(typers = typers, isGroup = isGroup)
            }
        }
    }

    selected?.let { msg ->
        val canReact = msg.status != MessageStatus.PENDING && canSend
        val myReaction = myUid?.let { msg.reactions[it] }
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = DakkDarkSurface,
            text = {
                Column {
                    if (canReact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Reactions.ALL.forEach { emoji ->
                                val chosen = emoji == myReaction
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (chosen) DakkYellow.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable {
                                            viewModel.react(peerUid, msg.id, emoji)
                                            selected = null
                                        }
                                ) {
                                    Text(emoji, fontSize = 26.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (canReact) {
                        TextButton(onClick = {
                            replyingTo = msg
                            selected = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Yanıtla")
                        }
                    }
                    if (msg.text != null) {
                        TextButton(onClick = {
                            copyToClipboard(context, msg.text, "Mesaj")
                            selected = null
                        }) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Kopyala")
                        }
                    }
                    if (msg.isFromMe && msg.status != MessageStatus.PENDING) {
                        TextButton(onClick = {
                            viewModel.deleteMessage(peerUid, msg.id)
                            if (replyingTo?.id == msg.id) replyingTo = null
                            selected = null
                        }) {
                            Icon(Icons.Default.Delete, null, tint = DakkStatusFailed)
                            Spacer(Modifier.width(8.dp))
                            Text("Herkesten sil", color = DakkStatusFailed)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Kapat") } }
        )
    }

    viewingImage?.let { mediaId ->
        ImageViewerDialog(
            file = viewModel.imageFile(mediaId),
            onSave = {
                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else viewModel.saveImageToGallery(mediaId)
            },
            onDismiss = { viewingImage = null }
        )
    }

    pendingMention?.let { text ->
        AiConsentDialog(
            inChat = true,
            onAccept = {
                viewModel.giveAiConsent("chat_$peerUid")
                pendingMention = null
                if (viewModel.sendMessage(peerUid, text, replyingTo)) {
                    draft = ""
                    replyingTo = null
                }
            },
            onDismiss = { pendingMention = null }
        )
    }

    if (creatingPoll) {
        CreatePollDialog(
            onDismiss = { creatingPoll = false },
            onCreate = { spec ->
                if (viewModel.sendPoll(peerUid, spec)) creatingPoll = false
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Sohbet temizlensin mi?") },
            text = {
                Text(
                    if (isGroup) "Mesajlar yalnızca bu cihazdan gizlenir. Grup üyeleri sohbeti görmeye devam eder."
                    else "Mesajlar yalnızca bu cihazdan gizlenir. Karşı taraf sohbeti görmeye devam eder."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearChat(peerUid) }) {
                    Text("Temizle", color = DakkStatusFailed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } }
        )
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("$title engellensin mi?") },
            text = { Text("Engellenen kişilerden gelen mesajlar ve bildirimler gösterilmez. Engeli Ayarlar'dan kaldırabilirsiniz.") },
            confirmButton = {
                TextButton(onClick = { confirmBlock = false; viewModel.setBlocked(peerUid, true) }) {
                    Text("Engelle", color = DakkStatusFailed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Vazgeç") } }
        )
    }
}

private sealed class ChatItem(val key: String) {
    class DateHeader(val label: String) : ChatItem("d_$label")
    class Message(val message: UiMessage) : ChatItem(message.id)
}

private fun buildChatItems(messages: List<UiMessage>): List<ChatItem> {
    val out = ArrayList<ChatItem>(messages.size + 8)
    var lastDay = ""
    val dayFormat = SimpleDateFormat("yyyyMMdd", turkish)
    messages.forEach { m ->
        val day = dayFormat.format(Date(m.timestamp))
        if (day != lastDay) {
            lastDay = day
            out.add(ChatItem.DateHeader(dayLabel(m.timestamp)))
        }
        out.add(ChatItem.Message(m))
    }
    return out
}

private fun dayLabel(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(now, then) -> "Bugün"
        same(yesterday, then) -> "Dün"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM EEEE", turkish).format(Date(time))
        else -> SimpleDateFormat("d MMMM yyyy", turkish).format(Date(time))
    }
}

@Composable
private fun DateChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(color = DakkDarkSurfaceVariant, shape = RoundedCornerShape(10.dp)) {
            Text(
                label,
                color = DakkTextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AssistantTypingRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = DakkYellow, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Dakk Asistan yazıyor…", color = DakkTextSecondaryDark, fontSize = 13.sp, fontStyle = FontStyle.Italic)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: UiMessage,
    senderLabel: String?,
    nameOf: (String) -> String,
    myUid: String?,
    canInteract: Boolean,
    onLongPress: () -> Unit,
    onOpenImage: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onReact: (String) -> Unit,
    onVote: (Int) -> Unit,
    onBuildTeams: () -> Unit,
    onOpenQuote: (ReplyQuote) -> Unit
) {
    val mine = message.isFromMe
    val image = message.image
    val chips = remember(message.reactions, myUid) { Reactions.summarize(message.reactions, myUid) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 18.dp
                    )
                )
                .background(
                    when {
                        message.isAssistant -> BubbleAssistant
                        mine -> BubbleMine
                        else -> BubbleTheirs
                    }
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = if (image != null) 4.dp else 12.dp, vertical = if (image != null) 4.dp else 8.dp)
        ) {
            if (senderLabel != null) {
                Text(
                    senderLabel,
                    color = senderColor(message.senderUid),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = if (image != null) 8.dp else 0.dp,
                        top = if (image != null) 4.dp else 0.dp,
                        bottom = 2.dp
                    )
                )
            }
            if (message.isAssistant) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = DakkYellow, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dakk Asistan", color = DakkYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            message.reply?.let { quote ->
                ReplyQuoteBox(
                    authorLabel = if (quote.fromMe) "Sen" else nameOf(quote.senderUid),
                    text = quote.text,
                    onClick = { onOpenQuote(quote) },
                    modifier = Modifier.padding(
                        start = if (image != null) 4.dp else 0.dp,
                        end = if (image != null) 4.dp else 0.dp,
                        top = if (image != null) 4.dp else 0.dp,
                        bottom = 6.dp
                    )
                )
            }
            when {
                image != null -> ImageMessageContent(
                    image = image,
                    onOpen = { onOpenImage(image.mediaId) },
                    onRetry = { onRetryImage(image.mediaId) },
                    onLongPress = onLongPress
                )
                message.poll != null -> PollContent(
                    poll = message.poll,
                    enabled = canInteract && message.status != MessageStatus.PENDING,
                    nameOf = nameOf,
                    onVote = onVote,
                    onBuildTeams = onBuildTeams
                )
                message.text != null -> Text(message.text, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                else -> Text(
                    "🔒 Bu mesaj bu cihazda çözülemedi (farklı bir cihazda veya eski bir kurulumda şifrelenmiş).",
                    color = DakkTextSecondaryDark,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = if (image != null) 6.dp else 0.dp, vertical = if (image != null) 2.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.expiresAt > 0) {
                    Icon(Icons.Default.Timer, "Kaybolan mesaj", tint = DakkTextTertiaryDark, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    SimpleDateFormat("HH:mm", turkish).format(Date(message.timestamp)),
                    color = DakkTextTertiaryDark,
                    fontSize = 11.sp
                )
                if (mine) {
                    Spacer(Modifier.width(4.dp))
                    when (message.status) {
                        MessageStatus.PENDING -> Icon(Icons.Default.Schedule, "Gönderiliyor", tint = DakkTextTertiaryDark, modifier = Modifier.size(14.dp))
                        MessageStatus.SENT -> Icon(Icons.Default.Done, "Gönderildi", tint = DakkTextTertiaryDark, modifier = Modifier.size(14.dp))
                        MessageStatus.READ -> Icon(Icons.Default.DoneAll, "Okundu", tint = DakkYellow, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        if (chips.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .offset(y = (-4).dp)
                    .padding(horizontal = 8.dp)
            ) {
                chips.forEach { chip ->
                    Surface(
                        color = if (chip.mine) DakkYellow.copy(alpha = 0.22f) else DakkDarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = chip.mine) { onReact(chip.emoji) }
                    ) {
                        Text(
                            if (chip.count > 1) "${chip.emoji} ${chip.count}" else chip.emoji,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Yanıtlanan mesajın baloncuk içindeki alıntısı. */
@Composable
private fun ReplyQuoteBox(authorLabel: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(40.dp)
                .background(DakkYellow)
        )
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(authorLabel, color = DakkYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                text,
                color = DakkTextSecondaryDark,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Giriş alanının üstünde, yanıtlanacak mesajı gösteren şerit. */
@Composable
private fun ReplyComposerBar(authorLabel: String, text: String, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DakkDarkSurfaceVariant)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(48.dp)
                .background(DakkYellow)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(authorLabel, color = DakkYellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(text, color = DakkTextSecondaryDark, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Yanıtı iptal et", tint = DakkTextSecondaryDark)
        }
    }
}

/** Takma ad, kaybolan mesaj ve grup değişiklikleri için ortada küçük not. */
@Composable
private fun SystemNoteRow(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Surface(color = DakkDarkSurfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(10.dp)) {
            Text(
                text,
                color = DakkTextSecondaryDark,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/** Sohbet içinde "… ekran görüntüsü aldı" satırı. */
@Composable
private fun ScreenshotNoticeRow(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(color = DakkDarkSurfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "📸 $label",
                color = DakkTextSecondaryDark,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
