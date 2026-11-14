package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.components.Avatar
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow

/**
 * Yeni grup: önce kişiler seçilir, sonra grup adı ve (isteğe bağlı) fotoğraf girilir.
 * [Screen.NewGroup.addToGroup] doluysa var olan gruba kişi ekleme ekranı olarak çalışır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(screen: Screen.NewGroup, viewModel: DakkViewModel) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val addingTo = screen.addToGroup
    val existing = if (addingTo != null) activeChat?.takeIf { it.chatId == addingTo }?.members?.map { it.uid }.orEmpty() else emptyList()
    var selected by rememberSaveable { mutableStateOf(screen.preselected.toList()) }
    var step by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }

    val limit = DakkViewModel.MAX_GROUP_MEMBERS - (if (addingTo != null) existing.size else 1)
    val candidates = contacts.filter { it.uid !in existing }
    val filtered = candidates.filter {
        query.isBlank() || it.alias.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true)
    }
    fun canInvite(uid: String) = !profiles[uid]?.publicKey.isNullOrBlank()

    BackHandler {
        if (step == 1) step = 0 else viewModel.closeNewGroup()
    }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (step == 1) step = 0 else viewModel.closeNewGroup() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Column {
                        Text(
                            when {
                                addingTo != null -> "Kişi ekle"
                                step == 0 -> "Yeni grup"
                                else -> "Grup bilgileri"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (step == 0) "${selected.size} / $limit kişi seçildi" else "${selected.size + 1} kişi",
                            color = DakkTextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(DakkDarkBg)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                val enabled = !busy && when {
                    step == 0 -> selected.isNotEmpty()
                    else -> name.isNotBlank()
                }
                Button(
                    onClick = {
                        when {
                            addingTo != null -> viewModel.addMembers(addingTo, selected)
                            step == 0 -> step = 1
                            else -> viewModel.createGroup(name, photo, selected)
                        }
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            when {
                                addingTo != null -> "Gruba ekle"
                                step == 0 -> "İleri"
                                else -> "Grubu oluştur"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (step == 0 && addingTo == null) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (step == 1 && addingTo == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(DakkDarkSurfaceVariant)
                        .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "Grup fotoğrafı",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(112.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.AddAPhoto, "Fotoğraf seç", tint = DakkYellow, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (photo == null) "Grup fotoğrafı (isteğe bağlı)" else "Değiştirmek için dokun",
                    color = DakkTextSecondaryDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(DakkViewModel.MAX_GROUP_NAME) },
                    label = { Text("Grup adı") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Text("Üyeler", color = DakkTextSecondaryDark, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    items(selected, key = { it }) { uid ->
                        val c = contacts.firstOrNull { it.uid == uid }
                        val label = c?.alias ?: profiles[uid]?.displayName ?: "Dakk Kullanıcısı"
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                            Avatar(label, c?.photoUrl ?: profiles[uid]?.photoUrl, size = 52.dp)
                            Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "🔒 Grup mesajları uçtan uca şifrelenir. Grubu kuran kişi yönetici olur.",
                    color = DakkTextTertiaryDark,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = selected.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(selected, key = { it }) { uid ->
                        val c = contacts.firstOrNull { it.uid == uid }
                        val label = c?.alias ?: profiles[uid]?.displayName ?: "Dakk Kullanıcısı"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { selected = selected - uid }
                        ) {
                            Box {
                                Avatar(label, c?.photoUrl ?: profiles[uid]?.photoUrl, size = 52.dp)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(DakkDarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Close, "Çıkar", tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                            Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text("Kişilerde ara") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DakkTextSecondaryDark) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DakkDarkSurfaceVariant,
                    unfocusedContainerColor = DakkDarkSurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = DakkYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (candidates.isEmpty()) {
                Text(
                    if (addingTo != null) "Eklenebilecek başka kişi yok. Önce kişilerine yeni arkadaşlar ekle."
                    else "Grup kurmak için önce kişilerine arkadaş ekle (Kişiler sekmesi → +).",
                    color = DakkTextSecondaryDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.uid }) { contact ->
                    val checked = contact.uid in selected
                    val allowed = canInvite(contact.uid)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = allowed) {
                                selected = when {
                                    checked -> selected - contact.uid
                                    selected.size >= limit -> selected
                                    else -> selected + contact.uid
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Avatar(contact.alias, contact.photoUrl, size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                contact.alias,
                                color = if (allowed) Color.White else DakkTextTertiaryDark,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (allowed) contact.email else "Dakk'ın güncel sürümünü kullanmıyor",
                                color = DakkTextSecondaryDark,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (checked) DakkYellow else DakkDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
