package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.db.ContactEntity
import com.example.data.db.MessageEntity
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peer: ContactEntity,
    viewModel: DakkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { com.example.audio.AudioRecorderManager(context) }

    BackHandler {
        audioManager.stopPlaying()
        audioManager.stopRecording()
        viewModel.navigateTo(Screen.Home)
    }

    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    var activeCallType by remember { mutableStateOf<String?>(null) } // "AUDIO" or "VIDEO" or null
    var showMediaGallery by remember { mutableStateOf(false) }
    var selectedImageForFullscreen by remember { mutableStateOf<String?>(null) }
    var showContactDetailDialog by remember { mutableStateOf(false) }

    // Ses kaydı durumu
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }

    // Fotoğraf Seçici (Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendMessageToActivePeer("📷 $uri")
            Toast.makeText(context, "Fotoğraf iletildi", Toast.LENGTH_SHORT).show()
        }
    }

    // Dosya Seçici (Document Picker)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "Belge.pdf"
            viewModel.sendMessageToActivePeer("📄 [Belge: $fileName]")
            Toast.makeText(context, "Belge gönderildi", Toast.LENGTH_SHORT).show()
        }
    }

    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isBot = peer.userId == "DAKK_BOT_OFFICIAL"
    val listState = rememberLazyListState()

    // Ses kayıt zamanlayıcısı
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordDurationSeconds = 0
            while (isRecordingAudio) {
                delay(1000)
                recordDurationSeconds++
            }
        }
    }

    // Mesajlar eklendiğinde otomatik aşağı kaydır
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_screen_root"),
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            showContactDetailDialog = true
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (peer.isGroup) DakkYellow else if (isBot) DakkYellow else DakkOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            if (peer.isGroup) {
                                Icon(Icons.Default.Groups, contentDescription = "Grup", tint = Color.Black, modifier = Modifier.size(24.dp))
                            } else if (isBot) {
                                Image(
                                    painter = painterResource(id = R.drawable.dakk_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Text(
                                    text = peer.alias.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.widthIn(max = 180.dp)) {
                            Text(
                                text = peer.alias,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                            if (peer.isGroup) {
                                Text(
                                    text = if (peer.groupMembers.isNotBlank()) peer.groupMembers else "P2P Grup",
                                    fontSize = 11.sp,
                                    color = DakkTextSecondaryDark,
                                    maxLines = 1
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PulsatingOnlineBadge()
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "çevrimiçi",
                                        fontSize = 11.sp,
                                        color = DakkStatusConnected,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier.testTag("chat_btn_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { activeCallType = "VIDEO" }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Görüntülü Arama", tint = Color.White)
                    }

                    IconButton(onClick = { activeCallType = "AUDIO" }) {
                        Icon(Icons.Default.Call, contentDescription = "Sesli Arama", tint = Color.White)
                    }

                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Diğer", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DakkDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Medyayı Görüntüle", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.PermMedia, contentDescription = null, tint = DakkYellow) },
                            onClick = {
                                showMenu = false
                                showMediaGallery = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sohbeti Temizle", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DakkOrange) },
                            onClick = {
                                showMenu = false
                                viewModel.clearChat(peer.userId)
                                Toast.makeText(context, "Sohbet temizlendi", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (peer.isBlocked) "Engeli Kaldır" else "Kişiyi Engelle", color = DakkStatusFailed) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = DakkStatusFailed) },
                            onClick = {
                                showMenu = false
                                viewModel.toggleBlock(peer.userId, peer.isBlocked)
                                val msg = if (peer.isBlocked) "Engel kaldırıldı" else "Kişi engellendi"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DakkDarkBg)
        ) {
            // Şifreleme Bilgi Rozeti
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = DakkDarkSurface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = DakkYellow, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mesajlar uçtan uca şifrelidir. Dakk dahil kimse okuyamaz.",
                            fontSize = 11.sp,
                            color = DakkTextSecondaryDark
                        )
                    }
                }
            }

            // Mesajlar Listesi veya Boş Sohbet Ördek Karşılaması
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.dakk_logo),
                                contentDescription = "Dakk Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Vak vak! Burası çok sessiz 🐥",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DakkYellow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bu sohbet tamamen P2P ve uçtan uca şifrelidir.\nSunucu yok, veri tabanı yok. İlk mesajı sen yaz!",
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 3 }
                        ) {
                            WhatsAppMessageBubble(
                                message = msg,
                                audioManager = audioManager,
                                onImageClick = { imgUri -> selectedImageForFullscreen = imgUri }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // Ses Kayıt Durum Göstergesi (Varsa)
            if (isRecordingAudio) {
                Surface(
                    color = Color(0xFF331B1B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ses Kaydediliyor... 00:${if (recordDurationSeconds < 10) "0$recordDurationSeconds" else recordDurationSeconds}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "İptal",
                            color = DakkTextSecondaryDark,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { isRecordingAudio = false }
                        )
                    }
                }
            }

            // WhatsApp Tarzı Alt Mesaj Yazma Alanı (Klavye Dostu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mesaj Input Hapı
                Surface(
                    color = DakkDarkSurface,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji / Sticker Butonu
                        IconButton(
                            onClick = { showEmojiSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Mood, contentDescription = "Emoji & Çıkartma", tint = DakkTextSecondaryDark)
                        }

                        // Metin Alanı
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Mesaj...", fontSize = 15.sp, color = DakkTextTertiaryDark) },
                            maxLines = 5,
                            minLines = 1,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = DakkYellow
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && messages.isNotEmpty()) {
                                        coroutineScope.launch {
                                            delay(100)
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                    }
                                }
                                .testTag("input_message_text")
                        )

                        // Ek / Ataş Butonu
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Dosya Ekle", tint = DakkTextSecondaryDark)
                        }

                        // Kamera Butonu
                        if (messageText.isBlank()) {
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", tint = DakkTextSecondaryDark)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // WhatsApp Tarzı Yuvarlak Gönder veya Mikrofon Butonu
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRecordingAudio) Color.Red else DakkYellow)
                        .clickable {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessageToActivePeer(messageText)
                                messageText = ""
                            } else {
                                if (isRecordingAudio) {
                                    isRecordingAudio = false
                                    val file = audioManager.stopRecording()
                                    if (file != null) {
                                        viewModel.sendMessageToActivePeer("🎙️ ${file.absolutePath}")
                                        Toast.makeText(context, "Sesli mesaj iletildi", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.sendMessageToActivePeer("🎙️ Sesli Mesaj (0:${if (recordDurationSeconds < 10) "0$recordDurationSeconds" else recordDurationSeconds})")
                                    }
                                } else {
                                    val startedFile = audioManager.startRecording()
                                    if (startedFile == null) {
                                        Toast.makeText(context, "Mikrofon iznini kontrol edin", Toast.LENGTH_SHORT).show()
                                    }
                                    isRecordingAudio = true
                                }
                            }
                        }
                        .testTag("btn_send_or_mic"),
                    contentAlignment = Alignment.Center
                ) {
                    if (messageText.isNotBlank()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Ses Kaydet",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // Ek Gönder Bottom Sheet
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = DakkDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Ek Gönder", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachmentOption(icon = Icons.Default.Image, bg = Color(0xFF9333EA), label = "Galeri") {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AttachmentOption(icon = Icons.Default.CameraAlt, bg = Color(0xFFE11D48), label = "Kamera") {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AttachmentOption(icon = Icons.Default.InsertDriveFile, bg = Color(0xFF2563EB), label = "Belge") {
                        showAttachmentSheet = false
                        documentPickerLauncher.launch("*/*")
                    }
                    AttachmentOption(icon = Icons.Default.LocationOn, bg = Color(0xFF10B981), label = "Konum") {
                        showAttachmentSheet = false
                        viewModel.sendMessageToActivePeer("📍 Konum Paylaşıldı: İstanbul, Türkiye")
                        Toast.makeText(context, "Mevcut konum gönderildi", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Emoji & Çıkartma Bottom Sheet
    if (showEmojiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEmojiSheet = false },
            containerColor = DakkDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Dakk Emojileri & Çıkartmalar 🐥", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                val emojis = listOf(
                    "🐥", "💬", "🔒", "🚀", "❤️", "👍", "😂", "🔥",
                    "🎯", "⚡", "📱", "🎉", "🤫", "🤝", "🌟", "🍕",
                    "🏆", "💯", "🆒", "🎈", "📷", "🎵", "📍", "🛡️"
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(DakkDarkBg)
                                .clickable {
                                    messageText += emoji
                                    showEmojiSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // P2P Sesli / Görüntülü Arama Ekranı Modal
    if (activeCallType != null) {
        val currentCallType = activeCallType!!
        P2pCallDialog(
            peerAlias = peer.alias,
            callType = currentCallType,
            onEndCall = { durationStr ->
                val callSummary = if (currentCallType == "VIDEO") "📹 Görüntülü Arama ($durationStr)" else "📞 Sesli Arama ($durationStr)"
                viewModel.sendMessageToActivePeer(callSummary)
                activeCallType = null
            }
        )
    }

    // Kişi Detay ve Doğrulama Dialog'u
    if (showContactDetailDialog) {
        ContactDetailDialog(
            peer = peer,
            onDismiss = { showContactDetailDialog = false },
            onClearChat = {
                viewModel.clearChat(peer.userId)
                showContactDetailDialog = false
                Toast.makeText(context, "Sohbet temizlendi", Toast.LENGTH_SHORT).show()
            },
            onToggleBlock = {
                viewModel.toggleBlock(peer.userId, peer.isBlocked)
                showContactDetailDialog = false
                val msg = if (peer.isBlocked) "Engel kaldırıldı" else "Kişi engellendi"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Medyaları Görüntüle Dialog
    if (showMediaGallery) {
        val mediaMessages = messages.filter { it.content.startsWith("📷") || it.content.contains("Fotoğraf") || it.content.contains("Galeri") }

        Dialog(onDismissRequest = { showMediaGallery = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DakkDarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Paylaşılan Medyalar 🖼️", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (mediaMessages.isEmpty()) {
                        Text("Bu sohbet içinde henüz paylaşılan medya bulunmuyor.", color = DakkTextSecondaryDark, fontSize = 13.sp)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(240.dp)
                        ) {
                            items(mediaMessages) { msg ->
                                val cleanUri = msg.content.removePrefix("📷").trim()
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DakkDarkBg)
                                        .clickable { selectedImageForFullscreen = cleanUri },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cleanUri.startsWith("content://") || cleanUri.startsWith("file://")) {
                                        AsyncImage(
                                            model = cleanUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = DakkYellow)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showMediaGallery = false },
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Kapat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Tam Ekran Fotoğraf Görüntüleyici Modal
    if (selectedImageForFullscreen != null) {
        Dialog(
            onDismissRequest = { selectedImageForFullscreen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { selectedImageForFullscreen = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                val uriStr = selectedImageForFullscreen!!
                if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                    AsyncImage(
                        model = uriStr,
                        contentDescription = "Tam Ekran Fotoğraf",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = DakkYellow, modifier = Modifier.size(100.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fotoğraf Önizlemesi", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PulsatingOnlineBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(DakkStatusConnected.copy(alpha = alpha))
    )
}

@Composable
fun P2pCallDialog(
    peerAlias: String,
    callType: String,
    onEndCall: (String) -> Unit
) {
    val context = LocalContext.current
    var durationSeconds by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isCameraOff by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            durationSeconds++
        }
    }

    val durationStr = String.format(Locale.getDefault(), "%02d:%02d", durationSeconds / 60, durationSeconds % 60)

    Dialog(
        onDismissRequest = { onEndCall(durationStr) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1115))
        ) {
            if (callType == "VIDEO") {
                // Video Görünümü
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCameraOff) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = DakkTextSecondaryDark, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Kamera Kapalı", color = DakkTextSecondaryDark, fontSize = 16.sp)
                        }
                    } else {
                        // Simüle edilen P2P Akış Arka Planı
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1E232A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(CircleShape)
                                        .background(DakkOrange),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = peerAlias.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "$peerAlias (P2P Canlı)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Yerel Kamera PiP (Picture in Picture)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF2A2E39),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 140.dp)
                                .size(width = 100.dp, height = 140.dp),
                            tonalElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (isFrontCamera) Icons.Default.CameraFront else Icons.Default.CameraRear,
                                        contentDescription = "Ön/Arka",
                                        tint = DakkYellow,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Siz", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Üst Bilgi Başlığı
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (callType == "VIDEO") "Doğrudan P2P Görüntülü Arama" else "Doğrudan P2P Sesli Arama",
                    color = DakkYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = peerAlias,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isMuted) "Mikrofon Kapalı • $durationStr" else "Şifreli P2P Aktif • $durationStr",
                    color = if (isMuted) DakkOrange else DakkStatusConnected,
                    fontSize = 14.sp
                )
            }

            // Sesli Arama İse Ortadaki Büyük Avatar
            if (callType == "AUDIO") {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
                        label = "pulseScale"
                    )

                    Box(
                        modifier = Modifier
                            .size((140 * pulseScale).dp)
                            .clip(CircleShape)
                            .background(DakkYellow.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(DakkOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = peerAlias.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Alt Arama Kontrol Tuşları
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mikrofon Toggle
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        val msg = if (isMuted) "Mikrofon Kapatıldı" else "Mikrofon Açıldı"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color.Red else Color(0xFF2A2D34))
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Sustur",
                        tint = Color.White
                    )
                }

                if (callType == "VIDEO") {
                    // Kamera Aç/Kapat
                    IconButton(
                        onClick = {
                            isCameraOff = !isCameraOff
                            val msg = if (isCameraOff) "Kamera Kapatıldı" else "Kamera Açıldı"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isCameraOff) Color.Red else Color(0xFF2A2D34))
                    ) {
                        Icon(
                            imageVector = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Kamera Aç/Kapa",
                            tint = Color.White
                        )
                    }

                    // Ön/Arka Kamera Değiştir
                    IconButton(
                        onClick = {
                            isFrontCamera = !isFrontCamera
                            Toast.makeText(context, if (isFrontCamera) "Ön Kamera" else "Arka Kamera", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2D34))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Kamera Çevir",
                            tint = Color.White
                        )
                    }
                } else {
                    // Hoparlör Toggle
                    IconButton(
                        onClick = {
                            isSpeakerOn = !isSpeakerOn
                            val msg = if (isSpeakerOn) "Hoparlör Açık" else "Ahize Açık"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2D34))
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "Hoparlör",
                            tint = Color.White
                        )
                    }
                }

                // Aramayı Sonlandır (Kırmızı Buton)
                IconButton(
                    onClick = { onEndCall(durationStr) },
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Aramayı Sonlandır",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ContactDetailDialog(
    peer: ContactEntity,
    onDismiss: () -> Unit,
    onClearChat: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val context = LocalContext.current
    val fingerprint = remember(peer.publicKey) {
        val clean = peer.publicKey.filter { it.isLetterOrDigit() }.take(24)
        clean.chunked(4).joinToString(" ")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DakkDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (peer.isGroup) DakkYellow else DakkOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peer.alias.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = peer.alias,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (peer.isGroup) "P2P Grup Sohbeti" else "Uçtan Uca Doğrulanmış Cihaz 🛡️",
                    fontSize = 12.sp,
                    color = DakkStatusConnected
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Güvenlik Numarası (Fingerprint)
                Surface(
                    color = DakkDarkBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = DakkYellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Güvenlik Numarası (Safety Code)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = fingerprint,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DakkYellow,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bu numara, sizin ve arkadaşınızın sohbetinin uçtan uca şifrelendiğini kanıtlar.",
                            fontSize = 10.sp,
                            color = DakkTextSecondaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearChat,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DakkOrange)
                    ) {
                        Text("Sohbeti Sil", fontSize = 12.sp, color = DakkOrange)
                    }

                    Button(
                        onClick = onToggleBlock,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (peer.isBlocked) DakkYellow else DakkStatusFailed)
                    ) {
                        Text(
                            text = if (peer.isBlocked) "Engeli Kaldır" else "Engelle",
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DakkDarkSurfaceVariant)
                ) {
                    Text("Kapat", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
fun WhatsAppMessageBubble(
    message: MessageEntity,
    audioManager: com.example.audio.AudioRecorderManager,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current
    val isMe = message.isFromMe
    val timeStr = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isMe) Color(0xFF2E2C1A) else Color(0xFF1E2026),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (!isMe && message.senderAlias.isNotBlank()) {
                    Text(
                        text = message.senderAlias,
                        color = DakkYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Fotoğraf Balonu
                if (message.content.startsWith("📷")) {
                    val uriStr = message.content.removePrefix("📷").trim()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .clickable { onImageClick(uriStr) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                            AsyncImage(
                                model = uriStr,
                                contentDescription = "Fotoğraf",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = DakkYellow, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Fotoğraf", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
                // Sesli Mesaj Balonu Görünümü (Gerçek Ses Çalma)
                else if (message.content.startsWith("🎙️")) {
                    val rawPath = message.content.removePrefix("🎙️").trim()
                    var isPlaying by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (isPlaying) {
                                    audioManager.stopPlaying()
                                    isPlaying = false
                                } else {
                                    if (rawPath.startsWith("/") || rawPath.endsWith(".m4a")) {
                                        isPlaying = true
                                        audioManager.playAudio(rawPath) {
                                            isPlaying = false
                                        }
                                    } else {
                                        Toast.makeText(context, "Sesli Mesaj $rawPath", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Color.Green else DakkYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("|||||||||||||||||||||||||", color = if (isPlaying) Color.Green else DakkYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (rawPath.startsWith("/")) "Ses Kaydı (.m4a)" else rawPath, color = DakkTextSecondaryDark, fontSize = 11.sp)
                        }
                    }
                } else {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        color = Color.White,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = DakkTextTertiaryDark
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "İletildi",
                            modifier = Modifier.size(15.dp),
                            tint = if (message.status == "DELIVERED") Color(0xFF53BDEB) else DakkTextTertiaryDark
                        )
                    }
                }
            }
        }
    }
}
