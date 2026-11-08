package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DakkViewModel
import com.example.ui.components.AiConsentDialog
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkStatusFailed
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow

private val AssistantBubble = Color(0xFF26233A)
private val UserBubble = Color(0xFF3D3410)

private val suggestions = listOf(
    "Bana bir mesaj yazmamda yardım et",
    "Bugün ne pişirsem?",
    "Bu metni kısalt: ",
    "Bana ilginç bir bilgi ver"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: DakkViewModel) {
    val context = LocalContext.current
    val turns by viewModel.assistantTurns.collectAsStateWithLifecycle()
    val busy by viewModel.assistantBusy.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    var draft by rememberSaveable { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(!viewModel.hasAiConsent(CONSENT_SCOPE)) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    BackHandler { viewModel.closeSecondaryScreen() }

    LaunchedEffect(turns.size, busy) {
        val count = 1 + turns.size + if (busy) 1 else 0
        if (count > 1) listState.animateScrollToItem(count - 1)
    }

    fun send() {
        if (!viewModel.hasAiConsent(CONSENT_SCOPE)) {
            showConsent = true
            return
        }
        if (viewModel.sendToAssistant(draft)) draft = ""
    }

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeSecondaryScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistantAvatar(40)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Dakk Asistan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Yapay zekâ tarafından işlenir", fontSize = 12.sp, color = DakkTextSecondaryDark)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { openUrl(context, AI_INFO_URL) }) {
                        Icon(Icons.Default.Info, contentDescription = "Ayrıntılar")
                    }
                    if (turns.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Sohbeti temizle")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .background(DakkDarkBg)
                    .navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = draft,
                    onValueChange = { if (it.length <= 4000) draft = it },
                    placeholder = { Text("Dakk Asistan'a sor…") },
                    enabled = config.assistantEnabled,
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
                val enabled = draft.isNotBlank() && !busy && config.assistantEnabled
                IconButton(
                    onClick = { send() },
                    enabled = enabled,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (enabled) DakkYellow else DakkDarkSurfaceVariant)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint = if (enabled) Color.Black else DakkTextTertiaryDark
                    )
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "intro") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AssistantAvatar(72)
                    Spacer(Modifier.height(12.dp))
                    Text("Merhaba! Ben Dakk Asistan.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (config.assistantEnabled) {
                            "Soru sorabilir, mesaj yazdırabilir, fikir alabilirsiniz. " +
                                "Bir sohbette @dakk yazarak beni o sohbete de çağırabilirsiniz."
                        } else {
                            "Dakk Asistan şu an kullanılamıyor. Daha sonra tekrar deneyin."
                        },
                        color = DakkTextSecondaryDark,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(color = DakkYellow.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "Bu sohbet yapay zekâ tarafından işlenir ve uçtan uca şifreli değildir.",
                            color = DakkYellow,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    if (turns.isEmpty() && config.assistantEnabled) {
                        Spacer(Modifier.height(16.dp))
                        suggestions.forEach { s ->
                            Surface(
                                color = DakkDarkSurfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { draft = s }
                            ) {
                                Text(s, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            itemsIndexed(turns, key = { i, t -> "${t.time}_$i" }) { _, turn ->
                val mine = turn.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (mine) UserBubble else AssistantBubble,
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (mine) 18.dp else 4.dp,
                            bottomEnd = if (mine) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                turn.text,
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            if (busy) {
                item(key = "typing") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(color = DakkYellow, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Dakk Asistan düşünüyor…", color = DakkTextSecondaryDark, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showConsent) {
        AiConsentDialog(
            inChat = false,
            onAccept = {
                viewModel.giveAiConsent(CONSENT_SCOPE)
                showConsent = false
            },
            onDismiss = {
                showConsent = false
                if (!viewModel.hasAiConsent(CONSENT_SCOPE)) viewModel.closeSecondaryScreen()
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Asistan sohbeti silinsin mi?") },
            text = { Text("Asistanın bu sohbetle ilgili hatırladıkları da silinir.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearAssistant() }) {
                    Text("Sil", color = DakkStatusFailed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
fun AssistantAvatar(sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xFF3A3360)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DakkYellow, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

private const val CONSENT_SCOPE = "assistant"
