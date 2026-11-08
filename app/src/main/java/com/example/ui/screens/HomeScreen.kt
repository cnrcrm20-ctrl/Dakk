package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatSummary
import com.example.data.Contact
import com.example.ui.DakkViewModel
import com.example.ui.HomeTab
import com.example.ui.components.Avatar
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: DakkViewModel) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chatsLoaded by viewModel.chatsLoaded.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAddContact by rememberSaveable { mutableStateOf(false) }

    val unreadCount = chats.count { it.isUnread }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    if (searching && tab != HomeTab.SETTINGS) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Ara…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.dakk_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when (tab) {
                                    HomeTab.CHATS -> "Dakk"
                                    HomeTab.CONTACTS -> "Kişiler"
                                    HomeTab.SETTINGS -> "Ayarlar"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = if (tab == HomeTab.CHATS) DakkYellow else Color.White
                            )
                        }
                    }
                },
                actions = {
                    if (tab != HomeTab.SETTINGS) {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(
                                if (searching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searching) "Aramayı kapat" else "Ara"
                            )
                        }
                        IconButton(onClick = { showAddContact = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Kişi ekle")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        },
        floatingActionButton = {
            if (tab == HomeTab.CHATS) {
                FloatingActionButton(
                    onClick = {
                        if (contacts.isEmpty()) showAddContact = true else viewModel.setTab(HomeTab.CONTACTS)
                    },
                    containerColor = DakkYellow,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Yeni sohbet")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = DakkDarkSurface) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = DakkYellow,
                    indicatorColor = DakkYellow,
                    unselectedIconColor = DakkTextSecondaryDark,
                    unselectedTextColor = DakkTextSecondaryDark
                )
                NavigationBarItem(
                    selected = tab == HomeTab.CHATS,
                    onClick = { viewModel.setTab(HomeTab.CHATS); searching = false; query = "" },
                    icon = {
                        BadgedBox(badge = {
                            if (unreadCount > 0) Badge(containerColor = DakkYellow, contentColor = Color.Black) {
                                Text("$unreadCount")
                            }
                        }) {
                            Icon(
                                if (tab == HomeTab.CHATS) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text("Sohbetler") },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = tab == HomeTab.CONTACTS,
                    onClick = { viewModel.setTab(HomeTab.CONTACTS); searching = false; query = "" },
                    icon = {
                        Icon(
                            if (tab == HomeTab.CONTACTS) Icons.Default.People else Icons.Default.PeopleOutline,
                            contentDescription = null
                        )
                    },
                    label = { Text("Kişiler") },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = tab == HomeTab.SETTINGS,
                    onClick = { viewModel.setTab(HomeTab.SETTINGS); searching = false; query = "" },
                    icon = {
                        Icon(
                            if (tab == HomeTab.SETTINGS) Icons.Default.Settings else Icons.Outlined.Settings,
                            contentDescription = null
                        )
                    },
                    label = { Text("Ayarlar") },
                    colors = itemColors
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                HomeTab.CHATS -> {
                    val filtered = remember(chats, query) {
                        if (query.isBlank()) chats
                        else chats.filter {
                            it.peerName.contains(query, ignoreCase = true) || it.preview.contains(query, ignoreCase = true)
                        }
                    }
                    when {
                        !chatsLoaded -> CenteredLoading()
                        config.assistantEnabled && query.isBlank() -> ChatList(
                            chats = filtered,
                            showAssistant = true,
                            onAssistant = { viewModel.openAssistant() },
                            onAddContact = { showAddContact = true }
                        ) { viewModel.openChat(it.peerUid) }
                        chats.isEmpty() -> EmptyState(
                            title = "Henüz sohbet yok",
                            message = "Bir arkadaşınızı ekleyin ve ilk şifreli mesajınızı gönderin.",
                            action = "Kişi ekle",
                            onAction = { showAddContact = true }
                        )
                        else -> ChatList(filtered, showAssistant = false) { viewModel.openChat(it.peerUid) }
                    }
                }
                HomeTab.CONTACTS -> {
                    val filtered = remember(contacts, query) {
                        if (query.isBlank()) contacts
                        else contacts.filter {
                            it.alias.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true)
                        }
                    }
                    if (contacts.isEmpty()) {
                        EmptyState(
                            title = "Kişi listeniz boş",
                            message = "QR kod, davet bağlantısı veya e-posta adresiyle arkadaşlarınızı ekleyin.",
                            action = "Kişi ekle",
                            onAction = { showAddContact = true }
                        )
                    } else {
                        ContactList(filtered, onAdd = { showAddContact = true }) { viewModel.openChat(it.uid) }
                    }
                }
                HomeTab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }

    if (showAddContact) {
        AddContactSheet(viewModel = viewModel, onDismiss = { showAddContact = false })
    }
}

@Composable
private fun ChatList(
    chats: List<ChatSummary>,
    showAssistant: Boolean,
    onAssistant: () -> Unit = {},
    onAddContact: () -> Unit = {},
    onClick: (ChatSummary) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        if (showAssistant) {
            item(key = "dakk_assistant") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAssistant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistantAvatar(52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dakk Asistan", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Yapay zekâ asistanınız — sorun, yazdırın, fikir alın",
                            color = DakkTextSecondaryDark,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(
                    color = DakkDarkBorder.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 82.dp)
                )
            }
            if (chats.isEmpty()) {
                item(key = "no_chats") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Henüz sohbet yok", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Bir arkadaşınızı ekleyin ve ilk şifreli mesajınızı gönderin.",
                            color = DakkTextSecondaryDark,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = onAddContact,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = DakkYellow,
                                contentColor = Color.Black
                            )
                        ) { Text("Kişi ekle") }
                    }
                }
            }
        }
        items(chats, key = { it.chatId }) { chat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(chat) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(chat.peerName, chat.peerPhotoUrl, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            chat.peerName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatListTime(chat.lastMessageTime),
                            color = if (chat.isUnread) DakkYellow else DakkTextTertiaryDark,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat.lastFromMe) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = DakkTextTertiaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            chat.preview,
                            color = if (chat.isUnread) Color.White else DakkTextSecondaryDark,
                            fontWeight = if (chat.isUnread) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (chat.isUnread) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(DakkYellow)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                color = DakkDarkBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 82.dp)
            )
        }
    }
}

@Composable
private fun ContactList(contacts: List<Contact>, onAdd: () -> Unit, onClick: (Contact) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DakkYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.Black)
                }
                Spacer(Modifier.width(14.dp))
                Text("Yeni kişi ekle", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(
                "KİŞİLERİM (${contacts.size})",
                color = DakkTextTertiaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(contacts, key = { it.uid }) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(contact) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(contact.alias, contact.photoUrl, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        contact.alias,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (contact.email.isNotBlank()) {
                        Text(
                            contact.email,
                            color = DakkTextSecondaryDark,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DakkYellow)
    }
}

@Composable
fun EmptyState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.dakk_logo), contentDescription = null, modifier = Modifier.size(76.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = DakkTextSecondaryDark, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(action, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun formatListTime(time: Long): String {
    if (time <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val locale = Locale.forLanguageTag("tr-TR")
    return when {
        sameDay -> SimpleDateFormat("HH:mm", locale).format(Date(time))
        isYesterday -> "Dün"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", locale).format(Date(time))
        else -> SimpleDateFormat("dd.MM.yyyy", locale).format(Date(time))
    }
}
