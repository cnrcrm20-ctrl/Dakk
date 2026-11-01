package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.ContactEntity
import com.example.data.db.MessageEntity
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
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
    BackHandler {
        viewModel.navigateTo(Screen.Home)
    }

    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }

    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isBot = peer.userId == "DAKK_BOT_OFFICIAL"
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_screen_root"),
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "${peer.alias} profili", Toast.LENGTH_SHORT).show()
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

                        Column(modifier = Modifier.widthIn(max = 200.dp)) {
                            Text(
                                text = peer.alias,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                            if (peer.isGroup) {
                                Text(
                                    text = if (peer.groupMembers.isNotBlank()) peer.groupMembers else "Grup Sohbeti",
                                    fontSize = 11.sp,
                                    color = DakkTextSecondaryDark,
                                    maxLines = 1
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(DakkStatusConnected)
                                    )
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
                    IconButton(onClick = {
                        Toast.makeText(context, "Doğrudan P2P görüntülü arama başlatılıyor...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Görüntülü Arama", tint = Color.White)
                    }

                    IconButton(onClick = {
                        Toast.makeText(context, "Doğrudan P2P sesli arama başlatılıyor...", Toast.LENGTH_SHORT).show()
                    }) {
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
                                Toast.makeText(context, "Paylaşılan medya yok", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sohbeti Temizle", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DakkOrange) },
                            onClick = {
                                showMenu = false
                                viewModel.clearChat(peer.userId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kişiyi Engelle", color = DakkStatusFailed) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = DakkStatusFailed) },
                            onClick = {
                                showMenu = false
                                viewModel.toggleBlock(peer.userId, peer.isBlocked)
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

            // Mesajlar Listesi
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    WhatsAppMessageBubble(message = msg)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // WhatsApp Tarzı Alt Mesaj Yazma Alanı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
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
                        // Emoji Butonu
                        IconButton(
                            onClick = { messageText += "🐥 " },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Mood, contentDescription = "Emoji", tint = DakkTextSecondaryDark)
                        }

                        // Metin Alanı
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Mesaj...", fontSize = 15.sp, color = DakkTextTertiaryDark) },
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
                                    viewModel.sendMessageToActivePeer("📷 [Fotoğraf]")
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
                        .background(DakkYellow)
                        .clickable {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessageToActivePeer(messageText)
                                messageText = ""
                            } else {
                                // Hızlı sesli mesaj simülasyonu
                                isRecordingAudio = !isRecordingAudio
                                if (!isRecordingAudio) {
                                    viewModel.sendMessageToActivePeer("🎙️ Sesli Mesaj (0:07)")
                                    Toast.makeText(context, "Sesli mesaj iletildi", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Kayıt yapılıyor... Bitirmek için tekrar basın", Toast.LENGTH_SHORT).show()
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
                            tint = if (isRecordingAudio) Color.Red else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

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
                        viewModel.sendMessageToActivePeer("🖼️ [Galeri Resmi]")
                    }
                    AttachmentOption(icon = Icons.Default.CameraAlt, bg = Color(0xFFE11D48), label = "Kamera") {
                        showAttachmentSheet = false
                        viewModel.sendMessageToActivePeer("📷 [Anlık Fotoğraf]")
                    }
                    AttachmentOption(icon = Icons.Default.InsertDriveFile, bg = Color(0xFF2563EB), label = "Belge") {
                        showAttachmentSheet = false
                        viewModel.sendMessageToActivePeer("📄 [Belge.pdf]")
                    }
                    AttachmentOption(icon = Icons.Default.Headphones, bg = Color(0xFFEA580C), label = "Ses") {
                        showAttachmentSheet = false
                        viewModel.sendMessageToActivePeer("🎵 [Ses Dosyası]")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
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
fun WhatsAppMessageBubble(message: MessageEntity) {
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

                // Sesli Mesaj Balonu Görünümü
                if (message.content.startsWith("🎙️")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(DakkYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("|||||||||||||||||||||||||", color = DakkYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("0:07", color = DakkTextSecondaryDark, fontSize = 11.sp)
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
