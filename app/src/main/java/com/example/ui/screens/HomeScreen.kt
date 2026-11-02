package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.db.ContactEntity
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DakkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val contacts by viewModel.activeContacts.collectAsStateWithLifecycle()
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter { it.alias.contains(searchQuery, ignoreCase = true) || it.lastMessage.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_root"),
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Sohbetlerde ara...", fontSize = 14.sp, color = DakkTextTertiaryDark) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.dakk_logo),
                                    contentDescription = "Dakk Logo",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Dakk",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = DakkYellow,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearching = !isSearching; if (!isSearching) searchQuery = "" }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Ara",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { showNewChatSheet = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "QR Tara", tint = Color.White)
                    }

                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DakkDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Yeni Grup", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = DakkYellow) },
                            onClick = {
                                showMenu = false
                                showCreateGroupDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Yeni Sohbet", color = Color.White) },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = DakkYellow) },
                            onClick = {
                                showMenu = false
                                showNewChatSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("WhatsApp'tan Davet Et", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF25D366)) },
                            onClick = {
                                showMenu = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "👋 Selam! Dakk ile WhatsApp yerine doğrudan cihazdan cihaza (P2P), uçtan uca şifreli ve %100 gizli mesajlaşalım! 🐥\n\nUygulamayı hemen indir:\nhttps://github.com/cnrcrm20-ctrl/Dakk/releases/latest/download/Dakk.apk")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Dakk'a Davet Et"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ayarlar", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray) },
                            onClick = {
                                showMenu = false
                                selectedTab = 2
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DakkDarkSurface,
                modifier = Modifier.testTag("home_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (contacts.isNotEmpty()) {
                                    Badge(containerColor = DakkYellow, contentColor = Color.Black) {
                                        Text("${contacts.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = "Sohbetler")
                        }
                    },
                    label = { Text("Sohbetler", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = DakkYellow,
                        indicatorColor = DakkYellow,
                        unselectedIconColor = DakkTextSecondaryDark,
                        unselectedTextColor = DakkTextSecondaryDark
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.PeopleAlt, contentDescription = "Kişiler") },
                    label = { Text("Kişiler", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = DakkYellow,
                        indicatorColor = DakkYellow,
                        unselectedIconColor = DakkTextSecondaryDark,
                        unselectedTextColor = DakkTextSecondaryDark
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = DakkYellow,
                        indicatorColor = DakkYellow,
                        unselectedIconColor = DakkTextSecondaryDark,
                        unselectedTextColor = DakkTextSecondaryDark
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showNewChatSheet = true },
                    containerColor = DakkYellow,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_new_chat")
                ) {
                    Icon(Icons.Default.AddComment, contentDescription = "Yeni Sohbet")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)).togetherWith(
                        fadeOut(animationSpec = tween(180))
                    )
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> WhatsAppChatsContent(
                        contacts = filteredContacts,
                        onContactClick = { contact ->
                            viewModel.navigateTo(Screen.ChatDetail(contact))
                        },
                        onNewChat = { showNewChatSheet = true }
                    )
                    1 -> ContactsTabContent(
                        contacts = contacts,
                        onContactClick = { contact ->
                            viewModel.navigateTo(Screen.ChatDetail(contact))
                        },
                        onInviteWhatsApp = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "👋 Selam! Dakk ile WhatsApp yerine doğrudan cihazdan cihaza (P2P), uçtan uca şifreli mesajlaşalım! 🐥\n\nUygulamayı hemen indir:\nhttps://github.com/cnrcrm20-ctrl/Dakk/releases/latest/download/Dakk.apk"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "WhatsApp ile Davet Gönder"))
                        },
                        onScanQr = { showNewChatSheet = true }
                    )
                    2 -> ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (showNewChatSheet) {
        NewChatBottomSheet(
            viewModel = viewModel,
            onCreateGroupClick = { showCreateGroupDialog = true },
            onDismiss = { showNewChatSheet = false }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            viewModel = viewModel,
            onDismiss = { showCreateGroupDialog = false }
        )
    }
}

@Composable
fun WhatsAppChatsContent(
    contacts: List<ContactEntity>,
    onContactClick: (ContactEntity) -> Unit,
    onNewChat: () -> Unit
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Henüz Sohbetiniz Yok",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "WhatsApp yerine kullanmaya başlamak için bir arkadaşını davet et veya Dakk Asistanı ile konuş.",
                        fontSize = 13.sp,
                        color = DakkTextSecondaryDark,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onNewChat,
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sohbet Başlat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            items(contacts, key = { it.userId }) { contact ->
                WhatsAppChatRow(
                    contact = contact,
                    onClick = { onContactClick(contact) }
                )
                HorizontalDivider(
                    color = DakkDarkBorder.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 74.dp)
                )
            }
        }
    }
}

@Composable
fun WhatsAppChatRow(
    contact: ContactEntity,
    onClick: () -> Unit
) {
    val isBot = contact.userId == "DAKK_BOT_OFFICIAL"
    val avatarBg = remember(contact.userId, contact.isGroup) {
        when {
            contact.isGroup -> DakkYellow
            isBot -> DakkYellow
            else -> DakkOrange
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_row_${contact.userId.take(8)}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // WhatsApp Profil Fotoğrafı Dairesi
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            if (contact.isGroup) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Grup",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            } else if (isBot) {
                Image(
                    painter = painterResource(id = R.drawable.dakk_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Text(
                    text = contact.alias.take(1).uppercase(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.alias,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contact.lastMessageTime > 0) {
                    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    Text(
                        text = sdf.format(Date(contact.lastMessageTime)),
                        fontSize = 12.sp,
                        color = DakkTextTertiaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    tint = DakkYellow,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (contact.lastMessage.isNotBlank()) contact.lastMessage else "Sohbeti açın...",
                    fontSize = 14.sp,
                    color = DakkTextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ContactsTabContent(
    contacts: List<ContactEntity>,
    onContactClick: (ContactEntity) -> Unit,
    onInviteWhatsApp: () -> Unit,
    onScanQr: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // WhatsApp İle Davet Butonu
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onInviteWhatsApp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WhatsApp ile Arkadaşını Çağır",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tek tıkla davet linki gönder, hemen konuşmaya başlayın",
                            fontSize = 12.sp,
                            color = Color(0xFFB4E3CD)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // QR Kod Tara Butonu
            Card(
                colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScanQr)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(DakkYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QR Kod Tara veya Göster",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Yüz yüze iken anında arkadaş ekleyin",
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DAKK KİŞİLERİM (${contacts.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DakkTextTertiaryDark,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        items(contacts, key = { it.userId }) { contact ->
            WhatsAppChatRow(
                contact = contact,
                onClick = { onContactClick(contact) }
            )
        }
    }
}
