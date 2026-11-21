package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatSummary
import com.example.ui.DakkViewModel
import com.example.ui.components.Avatar
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow

/** Mesaj iletme: sohbet seç (en fazla [DakkViewModel.MAX_FORWARD]), tek dokunuşla gönder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(
    preview: String,
    chats: List<ChatSummary>,
    onSend: (keys: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picked = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    val shown = remember(chats, query) {
        val q = query.trim()
        if (q.isEmpty()) chats else chats.filter { it.peerName.contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DakkDarkSurface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("İlet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.size(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DakkDarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("↪", color = DakkYellow, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(preview, color = DakkTextSecondaryDark, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(10.dp))
            TextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text("Sohbet ara") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DakkTextSecondaryDark) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DakkDarkSurfaceVariant,
                    unfocusedContainerColor = DakkDarkSurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = DakkYellow
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(6.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(shown, key = { it.chatId }) { chat ->
                    val key = chat.peerUid
                    val isPicked = key in picked
                    val full = !isPicked && picked.size >= DakkViewModel.MAX_FORWARD
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !full) { if (isPicked) picked.remove(key) else picked.add(key) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Avatar(chat.peerName, chat.peerPhotoUrl, size = 44.dp, model = chat.photoModel, isGroup = chat.isGroup)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            chat.peerName,
                            color = if (full) DakkTextTertiaryDark else Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .background(if (isPicked) DakkYellow else DakkDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPicked) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = { onSend(picked.toList()) },
                enabled = picked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (picked.size) {
                        0 -> "Sohbet seç"
                        1 -> "Gönder"
                        else -> "${picked.size} sohbete gönder"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
