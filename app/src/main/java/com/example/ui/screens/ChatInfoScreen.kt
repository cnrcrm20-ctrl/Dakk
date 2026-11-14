package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ImageState
import com.example.ui.ActiveChat
import com.example.ui.ChatMember
import com.example.ui.DakkViewModel
import com.example.ui.components.Avatar
import com.example.ui.components.ImageViewerDialog
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkStatusFailed
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.Disappearing
import com.example.util.MuteOptions
import java.io.File

private enum class InfoSheet { PROFILE, MUTE, DISAPPEARING, SETTINGS, PRIVACY, NICKNAMES, INVITE, MEMBERS }

private val urlRegex = Regex("""https?://[^\s]+""")

/**
 * Instagram tarzı sohbet/grup bilgileri ekranı: büyük fotoğraf, ad, üç hızlı düğme ve ayar satırları;
 * en altta sohbette paylaşılan fotoğraflar ve bağlantılar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(key: String, viewModel: DakkViewModel) {
    val context = LocalContext.current
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val chat = activeChat?.takeIf { it.key == key }

    var sheet by remember { mutableStateOf<InfoSheet?>(null) }
    var memberAction by remember { mutableStateOf<ChatMember?>(null) }
    var editingNick by remember { mutableStateOf<ChatMember?>(null) }
    var editingGroup by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    val pickCustomPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setCustomPhoto(key, uri)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = viewingImage
        if (granted && id != null) viewModel.saveImageToGallery(id)
    }

    BackHandler { viewModel.closeChatInfo() }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeChatInfo() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = { Text(if (chat?.isGroup == true) "Grup bilgileri" else "Ayrıntılar", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkBg)
            )
        }
    ) { padding ->
        if (chat == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Yükleniyor…", color = DakkTextSecondaryDark)
            }
            return@Scaffold
        }
        val now = System.currentTimeMillis()
        val muted = MuteOptions.isMuted(chat.muteUntil, now)
        val images = remember(messages) {
            messages.mapNotNull { m -> m.image?.takeIf { it.state == ImageState.READY && it.localPath != null } }.reversed()
        }
        val links = remember(messages) {
            messages.flatMap { m -> m.text?.let { t -> urlRegex.findAll(t).map { it.value.trimEnd('.', ',', ')', '!') }.toList() }.orEmpty() }
                .distinct().reversed().take(20)
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // ---- Başlık
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(chat.title, null, size = 96.dp, model = chat.photoModel, isGroup = chat.isGroup)
                Spacer(Modifier.height(12.dp))
                Text(
                    chat.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                if (chat.subtitle.isNotBlank()) {
                    Text(chat.subtitle, color = DakkTextSecondaryDark, fontSize = 14.sp)
                }
                if (!chat.isGroup) {
                    val nick = chat.member(chat.key)?.nickname
                    if (nick != null) {
                        Text("Adı: ${chat.member(chat.key)?.realName}", color = DakkTextTertiaryDark, fontSize = 12.sp)
                    }
                }
                if (chat.isGroup && chat.amAdmin) {
                    TextButton(onClick = { editingGroup = true }) {
                        Text("Adı ve fotoğrafı değiştir", color = DakkYellow, fontWeight = FontWeight.SemiBold)
                    }
                } else Spacer(Modifier.height(12.dp))
            }

            // ---- Hızlı düğmeler
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (chat.isGroup) {
                    QuickAction(Icons.Default.PersonAdd, "Ekle", enabled = chat.amAdmin) {
                        viewModel.openAddMembers(chat.chatId)
                    }
                } else {
                    QuickAction(Icons.Default.Person, "Profil") { sheet = InfoSheet.PROFILE }
                }
                QuickAction(Icons.Default.Search, "Ara") { viewModel.openChatSearch(key) }
                QuickAction(
                    if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    if (muted) "Sessizde" else "Sessize al",
                    highlighted = muted
                ) { sheet = InfoSheet.MUTE }
            }
            Spacer(Modifier.height(20.dp))

            // ---- Ayar satırları
            Surface(
                color = DakkDarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    if (chat.isGroup) {
                        InfoRow(
                            Icons.Default.Link, "Davet linki",
                            if (chat.inviteEnabled) "Açık" else "Kapalı"
                        ) { sheet = InfoSheet.INVITE }
                        InfoRow(Icons.Default.Group, "Kişiler", "${chat.members.size} kişi") { sheet = InfoSheet.MEMBERS }
                    } else {
                        InfoRow(Icons.Default.Timer, "Kaybolan mesajlar", Disappearing.label(chat.disappearSeconds)) {
                            sheet = InfoSheet.DISAPPEARING
                        }
                    }
                    InfoRow(Icons.Default.Tune, "Sohbet ayarları", null) { sheet = InfoSheet.SETTINGS }
                    InfoRow(Icons.Default.Shield, "Gizlilik ve güvenlik", null) { sheet = InfoSheet.PRIVACY }
                    InfoRow(Icons.Default.Edit, "Takma adlar", null) { sheet = InfoSheet.NICKNAMES }
                    if (chat.isGroup) {
                        InfoRow(Icons.Default.GroupAdd, "Yeni grup oluştur", null, showDivider = false) { viewModel.openNewGroup() }
                    } else {
                        InfoRow(Icons.Default.GroupAdd, "${chat.title} ile grup oluştur", null, showDivider = false) {
                            viewModel.openNewGroup(listOf(chat.key))
                        }
                    }
                }
            }

            // ---- Medya
            Spacer(Modifier.height(24.dp))
            Text(
                if (chat.isGroup) "Medya" else "Medya ve bağlantılar",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))
            if (images.isEmpty() && (chat.isGroup || links.isEmpty())) {
                Text(
                    "Bu sohbette henüz fotoğraf paylaşılmadı.",
                    color = DakkTextTertiaryDark,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            if (images.isNotEmpty()) {
                images.chunked(3).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { image ->
                            AsyncImage(
                                model = File(image.localPath!!),
                                contentDescription = "Fotoğraf",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewingImage = image.mediaId }
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (!chat.isGroup && links.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                links.forEach { link ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openUrl(context, link) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DakkDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Link, null, tint = DakkYellow, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(link, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (chat.isGroup && chat.isMember) {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { confirmLeave = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = DakkStatusFailed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gruptan ayrıl", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // ---- Alt sayfalar
        sheet?.let { current ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = sheetState,
                containerColor = DakkDarkSurface
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    when (current) {
                        InfoSheet.PROFILE -> ProfileSheet(
                            chat = chat,
                            isContact = contacts.any { it.uid == chat.key },
                            onChangePhoto = {
                                pickCustomPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onRemovePhoto = { viewModel.setCustomPhoto(chat.key, null) },
                            onAddContact = { chat.peer?.let { viewModel.confirmAddContact(it) }; sheet = null }
                        )
                        InfoSheet.MUTE -> MuteSheet(muted, chat.muteUntil) { duration ->
                            viewModel.setMute(key, duration)
                            sheet = null
                        }
                        InfoSheet.DISAPPEARING -> DisappearingSheet(chat.disappearSeconds) { seconds ->
                            viewModel.setDisappearing(key, seconds)
                            sheet = null
                        }
                        InfoSheet.SETTINGS -> {
                            SheetTitle("Sohbet ayarları")
                            if (chat.isGroup) {
                                SheetAction(Icons.Default.Timer, "Kaybolan mesajlar", Disappearing.label(chat.disappearSeconds)) {
                                    sheet = InfoSheet.DISAPPEARING
                                }
                            } else {
                                SheetAction(
                                    Icons.Default.AddAPhoto,
                                    "Fotoğrafı değiştir",
                                    "Yalnızca senin telefonunda görünür"
                                ) {
                                    sheet = null
                                    pickCustomPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                if (chat.hasCustomPhoto) {
                                    SheetAction(Icons.Default.Refresh, "Asıl fotoğrafa dön", null) {
                                        viewModel.setCustomPhoto(chat.key, null); sheet = null
                                    }
                                }
                            }
                            SheetAction(Icons.Default.DeleteSweep, "Sohbeti temizle", "Mesajlar yalnızca bu cihazdan gizlenir") {
                                sheet = null; confirmClear = true
                            }
                            if (!chat.isGroup) {
                                SheetAction(Icons.Default.Block, "Engelle", null, danger = true) {
                                    sheet = null; confirmBlock = true
                                }
                            }
                        }
                        InfoSheet.PRIVACY -> PrivacySheet(chat, viewModel)
                        InfoSheet.NICKNAMES -> {
                            SheetTitle("Takma adlar")
                            Text(
                                if (chat.isGroup) "Takma adları gruptaki herkes görür. Herkes kendi takma adını değiştirebilir; yöneticiler herkesinkini."
                                else "Takma adları sohbetteki iki kişi de görür.",
                                color = DakkTextSecondaryDark,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                            chat.members.forEach { member ->
                                val canEdit = !chat.isGroup || member.isMe || chat.amAdmin
                                MemberRow(
                                    member = member,
                                    subtitle = member.nickname?.let { "Takma ad: $it" } ?: "Takma ad yok",
                                    enabled = canEdit && chat.isMember,
                                    trailing = { if (canEdit) Icon(Icons.Default.Edit, null, tint = DakkTextSecondaryDark, modifier = Modifier.size(18.dp)) }
                                ) { editingNick = member }
                            }
                        }
                        InfoSheet.INVITE -> InviteSheet(chat, viewModel)
                        InfoSheet.MEMBERS -> {
                            SheetTitle("Kişiler · ${chat.members.size}")
                            if (chat.amAdmin) {
                                SheetAction(Icons.Default.PersonAdd, "Kişi ekle", null) {
                                    sheet = null; viewModel.openAddMembers(chat.chatId)
                                }
                            }
                            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                                items(chat.members, key = { it.uid }) { member ->
                                    MemberRow(
                                        member = member,
                                        subtitle = member.email.ifBlank { null },
                                        enabled = !member.isMe,
                                        trailing = {
                                            if (member.isAdmin) {
                                                Surface(color = DakkYellow.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                                    Text(
                                                        "Yönetici",
                                                        color = DakkYellow,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    ) { memberAction = member }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    memberAction?.let { member ->
        val chatNow = chat
        AlertDialog(
            onDismissRequest = { memberAction = null },
            containerColor = DakkDarkSurface,
            icon = { Avatar(member.name, member.photoUrl, size = 56.dp) },
            title = { Text(member.name, textAlign = TextAlign.Center) },
            text = {
                Column {
                    TextButton(onClick = { memberAction = null; viewModel.openChat(member.uid) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mesaj gönder")
                    }
                    if (chatNow != null && chatNow.amAdmin) {
                        TextButton(onClick = {
                            viewModel.setAdmin(chatNow.chatId, member.uid, !member.isAdmin)
                            memberAction = null
                        }) {
                            Icon(Icons.Default.AdminPanelSettings, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (member.isAdmin) "Yöneticilikten çıkar" else "Yönetici yap")
                        }
                        TextButton(onClick = {
                            viewModel.removeMember(chatNow.chatId, member.uid)
                            memberAction = null
                        }) {
                            Icon(Icons.Default.PersonRemove, null, tint = DakkStatusFailed)
                            Spacer(Modifier.width(8.dp))
                            Text("Gruptan çıkar", color = DakkStatusFailed)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { memberAction = null }) { Text("Kapat") } }
        )
    }

    editingNick?.let { member ->
        var value by rememberSaveable(member.uid) { mutableStateOf(member.nickname.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNick = null },
            containerColor = DakkDarkSurface,
            title = { Text(if (member.isMe) "Takma adın" else "${member.realName} için takma ad") },
            text = {
                Column {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(DakkViewModel.MAX_NICKNAME) },
                        singleLine = true,
                        placeholder = { Text(member.realName) },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Sohbetteki herkes bu takma adı görür.", color = DakkTextTertiaryDark, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNickname(key, member.uid, value)
                    editingNick = null
                }) { Text("Kaydet", color = DakkYellow) }
            },
            dismissButton = {
                Row {
                    if (member.nickname != null) {
                        TextButton(onClick = {
                            viewModel.setNickname(key, member.uid, null)
                            editingNick = null
                        }) { Text("Kaldır", color = DakkStatusFailed) }
                    }
                    TextButton(onClick = { editingNick = null }) { Text("Vazgeç") }
                }
            }
        )
    }

    if (editingGroup && chat != null) {
        EditGroupDialog(
            chat = chat,
            onDismiss = { editingGroup = false },
            onSave = { name, photo, removePhoto ->
                viewModel.renameGroup(chat.chatId, name)
                if (photo != null) viewModel.setGroupPhoto(chat.chatId, photo)
                else if (removePhoto) viewModel.setGroupPhoto(chat.chatId, null)
                editingGroup = false
            }
        )
    }

    if (confirmLeave && chat != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Gruptan ayrılınsın mı?") },
            text = {
                Text(
                    if (chat.amAdmin && chat.members.count { it.isAdmin } == 1 && chat.members.size > 1)
                        "Gruptaki tek yönetici sensin. Ayrılırsan başka bir üye otomatik olarak yönetici olur."
                    else "Gruptan ayrılırsan yeni mesajları göremezsin."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leaveGroup(chat.chatId) }) {
                    Text("Ayrıl", color = DakkStatusFailed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Vazgeç") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Sohbet temizlensin mi?") },
            text = { Text("Mesajlar yalnızca bu cihazdan gizlenir. Diğer kişiler sohbeti görmeye devam eder.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearChat(key) }) {
                    Text("Temizle", color = DakkStatusFailed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } }
        )
    }

    if (confirmBlock && chat != null) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("${chat.title} engellensin mi?") },
            text = { Text("Engellenen kişilerden gelen mesajlar ve bildirimler gösterilmez. Engeli Ayarlar'dan kaldırabilirsiniz.") },
            confirmButton = {
                TextButton(onClick = { confirmBlock = false; viewModel.setBlocked(key, true) }) {
                    Text("Engelle", color = DakkStatusFailed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Vazgeç") } }
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
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (highlighted) DakkYellow.copy(alpha = 0.18f) else DakkDarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = when {
                    !enabled -> DakkTextTertiaryDark
                    highlighted -> DakkYellow
                    else -> Color.White
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (enabled) Color.White else DakkTextTertiaryDark, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String?,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(icon, null, tint = DakkYellow, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) {
                Text(value, color = DakkTextSecondaryDark, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DakkTextTertiaryDark)
        }
        if (showDivider) HorizontalDivider(color = DakkDarkBorder, modifier = Modifier.padding(start = 52.dp))
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SheetAction(icon: ImageVector, title: String, subtitle: String?, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(icon, null, tint = if (danger) DakkStatusFailed else DakkYellow)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) DakkStatusFailed else Color.White, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = DakkTextSecondaryDark, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) DakkYellow else DakkDarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MemberRow(
    member: ChatMember,
    subtitle: String?,
    enabled: Boolean,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Avatar(member.name, member.photoUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (member.isMe) "${member.name} (sen)" else member.name,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, color = DakkTextSecondaryDark, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
private fun ProfileSheet(
    chat: ActiveChat,
    isContact: Boolean,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onAddContact: () -> Unit
) {
    val member = chat.member(chat.key)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(chat.title, null, size = 120.dp, model = chat.photoModel)
        Spacer(Modifier.height(12.dp))
        Text(chat.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (member?.nickname != null) Text("Adı: ${member.realName}", color = DakkTextSecondaryDark, fontSize = 13.sp)
        if (!member?.email.isNullOrBlank()) Text(member!!.email, color = DakkTextSecondaryDark, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
    }
    SheetAction(Icons.Default.AddAPhoto, "Fotoğrafı değiştir", "Yalnızca senin telefonunda görünür", onClick = onChangePhoto)
    if (chat.hasCustomPhoto) SheetAction(Icons.Default.Refresh, "Asıl fotoğrafa dön", null, onClick = onRemovePhoto)
    if (!isContact && chat.peer != null) SheetAction(Icons.Default.PersonAdd, "Kişilere ekle", null, onClick = onAddContact)
}

@Composable
private fun MuteSheet(muted: Boolean, until: Long, onSelect: (Long?) -> Unit) {
    SheetTitle("Sessize al")
    if (muted) {
        Text(
            MuteOptions.label(until, System.currentTimeMillis())?.let { "Şu an sessizde ($it)" } ?: "Şu an sessizde",
            color = DakkTextSecondaryDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    } else {
        Text(
            "Bildirimler yalnızca bu telefonda susturulur.",
            color = DakkTextSecondaryDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
    Spacer(Modifier.height(4.dp))
    ChoiceRow("8 saat", false) { onSelect(MuteOptions.EIGHT_HOURS) }
    ChoiceRow("1 hafta", false) { onSelect(MuteOptions.ONE_WEEK) }
    ChoiceRow("Her zaman", muted && until == MuteOptions.FOREVER) { onSelect(MuteOptions.FOREVER) }
    if (muted) SheetAction(Icons.Default.Notifications, "Sesi aç", null) { onSelect(null) }
}

@Composable
private fun DisappearingSheet(current: Long, onSelect: (Long) -> Unit) {
    SheetTitle("Kaybolan mesajlar")
    Text(
        "Açıkken yeni mesajlar seçilen süreden sonra herkes için silinir. Daha önce gönderilen mesajlar etkilenmez.",
        color = DakkTextSecondaryDark,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
    )
    Disappearing.OPTIONS.forEach { seconds ->
        ChoiceRow(Disappearing.label(seconds), current == seconds) { onSelect(seconds) }
    }
}

@Composable
private fun PrivacySheet(chat: ActiveChat, viewModel: DakkViewModel) {
    SheetTitle("Gizlilik ve güvenlik")
    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Lock, null, tint = DakkYellow, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (chat.isGroup) "Grup mesajları, fotoğraflar, anketler ve tepkiler uçtan uca şifrelenir. Her mesaj için ayrı bir anahtar üretilir ve yalnızca grup üyelerine iletilir."
            else "Mesajlar, fotoğraflar ve tepkiler uçtan uca şifrelenir. Dakk dahil hiç kimse içeriğini okuyamaz.",
            color = DakkTextSecondaryDark,
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(8.dp))
    val others = chat.members.filter { !it.isMe }
    others.take(if (chat.isGroup) 50 else 1).forEach { member ->
        val safety = remember(member.uid) { viewModel.safetyNumberFor(member.uid) }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                if (chat.isGroup) "${member.name} · güvenlik numarası" else "Güvenlik numarası",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                safety ?: "Bu kişi henüz şifreleme anahtarı yayınlamadı.",
                color = if (safety != null) DakkYellow else DakkTextTertiaryDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
    Text(
        "Bu numarayı karşınızdaki kişinin ekranındakiyle karşılaştırın. Aynıysa sohbetiniz güvenlidir.",
        color = DakkTextTertiaryDark,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun InviteSheet(chat: ActiveChat, viewModel: DakkViewModel) {
    val context = LocalContext.current
    SheetTitle("Davet linki")
    Text(
        "Bağlantıya sahip olan herkes gruba katılabilir. Bağlantıyı yalnızca güvendiğin kişilerle paylaş.",
        color = DakkTextSecondaryDark,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
    )
    if (chat.amAdmin) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text("Davet linkini aç", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = chat.inviteEnabled,
                onCheckedChange = { viewModel.setInviteEnabled(chat.chatId, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DakkYellow, checkedThumbColor = Color.Black)
            )
        }
    }
    val link = chat.inviteLink
    if (chat.inviteEnabled && link != null) {
        Surface(
            color = DakkDarkSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                link,
                color = DakkYellow,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        SheetAction(Icons.Default.ContentCopy, "Bağlantıyı kopyala", null) { copyToClipboard(context, link, "Davet linki") }
        SheetAction(Icons.Default.Share, "Paylaş", null) {
            val text = "\"${chat.title}\" grubuna Dakk'ta katıl:\n$link\n\nDakk'ı indir: https://github.com/cnrcrm20-ctrl/Dakk/releases/latest"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Davet linkini paylaş"))
        }
        if (chat.amAdmin) {
            SheetAction(Icons.Default.Refresh, "Bağlantıyı sıfırla", "Eski bağlantı artık çalışmaz") {
                viewModel.resetInviteLink(chat.chatId)
            }
        }
    } else if (!chat.amAdmin) {
        Text(
            "Davet linki kapalı. Yalnızca grup yöneticileri açabilir.",
            color = DakkTextTertiaryDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun EditGroupDialog(chat: ActiveChat, onDismiss: () -> Unit, onSave: (String, Uri?, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(chat.title) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photo = uri; removePhoto = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DakkDarkSurface,
        title = { Text("Adı ve fotoğrafı değiştir") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        photo != null -> AsyncImage(
                            model = photo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(CircleShape)
                        )
                        removePhoto -> Avatar(name, null, size = 96.dp, isGroup = true)
                        else -> Avatar(name, null, size = 96.dp, model = chat.photoModel, isGroup = true)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DakkYellow),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AddAPhoto, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
                }
                if (chat.photoModel != null || photo != null) {
                    TextButton(onClick = { photo = null; removePhoto = true }) {
                        Text("Fotoğrafı kaldır", color = DakkStatusFailed, fontSize = 13.sp)
                    }
                } else Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(DakkViewModel.MAX_GROUP_NAME) },
                    singleLine = true,
                    label = { Text("Grup adı") },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, photo, removePhoto) }, enabled = name.isNotBlank()) {
                Text("Kaydet", color = DakkYellow)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DakkYellow,
    focusedLabelColor = DakkYellow,
    cursorColor = DakkYellow,
    unfocusedBorderColor = DakkDarkBorder
)
