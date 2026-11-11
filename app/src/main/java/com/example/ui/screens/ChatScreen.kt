package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.text.font.FontFamily
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(peerUid: String, viewModel: DakkViewModel) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val assistantBusyFor by viewModel.chatAssistantBusy.collectAsStateWithLifecycle()

    val peer = profiles[peerUid]
    val contact = contacts.firstOrNull { it.uid == peerUid }
    val peerName = peer?.displayName?.ifBlank { null } ?: contact?.alias ?: "Dakk Kullanıcısı"
    val peerPhoto = peer?.photoUrl?.ifBlank { null } ?: contact?.photoUrl
    val isContact = contact != null
    val canSend = peer != null && peer.publicKey.isNotBlank()

    var draft by rememberSaveable(peerUid) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<UiMessage?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var pendingMention by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember(peerUid) { mutableStateOf<UiMessage?>(null) }
    val scope = rememberCoroutineScope()
    val myUid = viewModel.currentUid

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
    val assistantTyping = assistantBusyFor == peerUid
    val listState = rememberLazyListState()
    val chatItems = remember(allMessages) { buildChatItems(allMessages) }

    BackHandler { viewModel.closeChat() }

    val showUnknownBanner = !isContact && peer != null && messages.any { !it.isFromMe }
    LaunchedEffect(chatItems.size, assistantTyping) {
        val headerCount = if (showUnknownBanner) 2 else 1
        val extra = if (assistantTyping) 1 else 0
        if (chatItems.isNotEmpty()) listState.animateScrollToItem(headerCount + chatItems.size - 1 + extra)
    }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeChat() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showInfo = true }
                    ) {
                        Avatar(peerName, peerPhoto, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                peerName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = DakkYellow, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Uçtan uca şifreli", fontSize = 12.sp, color = DakkTextSecondaryDark)
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
                            text = { Text("Güvenlik numarası") },
                            leadingIcon = { Icon(Icons.Default.VerifiedUser, null) },
                            onClick = { menuOpen = false; showInfo = true }
                        )
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
                        DropdownMenuItem(
                            text = { Text("Sohbeti temizle") },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                            onClick = { menuOpen = false; confirmClear = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Engelle", color = DakkStatusFailed) },
                            leadingIcon = { Icon(Icons.Default.Block, null, tint = DakkStatusFailed) },
                            onClick = { menuOpen = false; confirmBlock = true }
                        )
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
                if (!canSend && peer != null) {
                    Text(
                        "$peerName henüz Dakk'ın güncel sürümüne giriş yapmadı. Giriş yaptığında mesaj gönderebileceksiniz.",
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
                        authorLabel = if (target.isFromMe) "Sen" else peerName,
                        text = ReplySnippets.of(target.text, target.image != null),
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
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Fotoğraf gönder",
                                tint = if (canSend) DakkYellow else DakkTextTertiaryDark
                            )
                        }
                    }
                    TextField(
                        value = draft,
                        onValueChange = { if (it.length <= DakkViewModel.MAX_MESSAGE_CHARS) draft = it },
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
                            "🔒 Mesajlar uçtan uca şifrelenir. Dakk dahil hiç kimse içeriğini okuyamaz.",
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
            items(chatItems, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.DateHeader -> DateChip(item.label)
                    is ChatItem.Message -> if (item.message.isScreenshotNotice) {
                        ScreenshotNoticeRow(ScreenshotNotice.label(item.message.isFromMe, peerName))
                    } else MessageBubble(
                        message = item.message,
                        peerName = peerName,
                        myUid = myUid,
                        onLongPress = { if (!item.message.id.startsWith("upload_")) selected = item.message },
                        onOpenImage = { id -> viewingImage = id },
                        onRetryImage = { id -> viewModel.retryImage(peerUid, id) },
                        onReact = { emoji -> viewModel.react(peerUid, item.message.id, emoji) },
                        onOpenQuote = { quoted ->
                            val index = chatItems.indexOfFirst { it.key == quoted.messageId }
                            if (index >= 0) {
                                val headerCount = if (showUnknownBanner) 2 else 1
                                scope.launch { listState.animateScrollToItem(headerCount + index) }
                            }
                        }
                    )
                }
            }
            if (assistantTyping) {
                item(key = "assistant_typing") { AssistantTypingRow() }
            }
        }
    }

    selected?.let { msg ->
        val canReact = msg.status != MessageStatus.PENDING
        val myReaction = myUid?.let { msg.reactions[it] }
        AlertDialog(
            onDismissRequest = { selected = null },
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
                    if (canReact && canSend) {
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

    if (showInfo) {
        val safety = viewModel.safetyNumberFor(peerUid)
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Avatar(peerName, peerPhoto, size = 64.dp) },
            title = { Text(peerName) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    peer?.email?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = DakkTextSecondaryDark)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("Güvenlik numarası", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        safety ?: "Bu kişi henüz şifreleme anahtarı yayınlamadı.",
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Bu numarayı karşınızdaki kişinin ekranındakiyle karşılaştırın. Aynıysa sohbetiniz güvenlidir.",
                        color = DakkTextSecondaryDark,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Kapat") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Sohbet temizlensin mi?") },
            text = { Text("Mesajlar yalnızca bu cihazdan gizlenir. Karşı taraf sohbeti görmeye devam eder.") },
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
            title = { Text("$peerName engellensin mi?") },
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
    peerName: String,
    myUid: String?,
    onLongPress: () -> Unit,
    onOpenImage: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onReact: (String) -> Unit,
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
            if (message.isAssistant) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = DakkYellow, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dakk Asistan", color = DakkYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            message.reply?.let { quote ->
                ReplyQuoteBox(
                    authorLabel = if (quote.fromMe) "Sen" else peerName,
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
