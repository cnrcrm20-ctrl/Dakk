package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaling.QrCodeHelper
import com.example.signaling.SignalingPayload
import com.example.ui.DakkViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatBottomSheet(
    viewModel: DakkViewModel,
    onCreateGroupClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeView by remember { mutableStateOf<String?>("MENU") } // MENU, SHOW_QR, PASTE_LINK, PIN
    var pasteInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    val myOffer by viewModel.myOfferPayload.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingOffer.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DakkDarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DakkTextTertiaryDark) },
        modifier = Modifier.testTag("sheet_new_chat")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            when (activeView) {
                "MENU" -> {
                    Text(
                        text = "Yeni Sohbet Başlat",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Arkadaşınla WhatsApp'tan davet paylaş, grup kur veya QR ile eşleş",
                        fontSize = 13.sp,
                        color = DakkTextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 0. Yeni Grup Oluştur
                    QuickActionRow(
                        icon = Icons.Default.GroupAdd,
                        iconBg = DakkYellow,
                        iconTint = Color.Black,
                        title = "Yeni Grup Oluştur",
                        subtitle = "Birden fazla arkadaşınla doğrudan şifreli P2P grup sohbeti başlat",
                        onClick = {
                            onDismiss()
                            onCreateGroupClick()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. WhatsApp ile Davet Bağlantısı Gönder
                    QuickActionRow(
                        icon = Icons.Default.Share,
                        iconBg = Color(0xFF25D366), // WhatsApp Yeşili
                        title = "WhatsApp ile Davet Et",
                        subtitle = "Arkadaşına doğrudan WhatsApp üzerinden sohbet bağlantısı gönder",
                        onClick = {
                            if (myOffer == null) {
                                viewModel.createOfferForHandshake()
                            }
                            val offer = myOffer
                            val link = offer?.toInvitationLink() ?: "dakk://invite?data=setup"
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Selam! WhatsApp yerine Dakk üzerinden doğrudan P2P ve güvenli konuşalım. Sohbete katılmak için tıkla:\n$link"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "WhatsApp ile Davet Gönder"))
                            onDismiss()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. QR Kodumu Göster / Tara
                    QuickActionRow(
                        icon = Icons.Default.QrCodeScanner,
                        iconBg = DakkYellow,
                        iconTint = Color.Black,
                        title = "Dakk QR Kodum",
                        subtitle = "Yanındaki arkadaşına göster, anında sohbet başlasın",
                        onClick = {
                            if (myOffer == null) {
                                viewModel.createOfferForHandshake()
                            }
                            activeView = "SHOW_QR"
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Davet Bağlantısı Yapıştır
                    QuickActionRow(
                        icon = Icons.Default.Link,
                        iconBg = DakkOrange,
                        title = "Gelen Bağlantıyı Yapıştır",
                        subtitle = "Sana WhatsApp'tan gelen davet linkini yapıştır ve sohbete gir",
                        onClick = { activeView = "PASTE_LINK" }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. 6 Haneli Hızlı PIN
                    QuickActionRow(
                        icon = Icons.Default.Dialpad,
                        iconBg = Color(0xFF3B82F6),
                        title = "6 Haneli Hızlı Kod ile Eşleş",
                        subtitle = "Ortak bir 6 haneli kod belirleyerek uzaktan tek tıkla bağlanın",
                        onClick = { activeView = "PIN" }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                "SHOW_QR" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { activeView = "MENU" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                        Text(
                            text = "Dakk QR Kodum",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (myOffer != null) {
                            val qrBitmap = remember(myOffer) {
                                val compact = myOffer!!.encodeToCompactString()
                                QrCodeHelper.generateQrBitmap(compact, 400, 400)
                            }

                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White, RoundedCornerShape(16.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Dakk QR",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Arkadaşınız kamerasını bu koda doğrulttuğunda sohbet hemen başlar.",
                                fontSize = 12.sp,
                                color = DakkTextSecondaryDark,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Dakk Link", myOffer!!.toInvitationLink()))
                                    Toast.makeText(context, "Bağlantı panoya kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bağlantıyı Kopyala", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            CircularProgressIndicator(color = DakkYellow)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("QR Kod Hazırlanıyor...", color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                "PASTE_LINK" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { activeView = "MENU" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                        Text(
                            text = "Davet Bağlantısını Yapıştır",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pasteInput,
                        onValueChange = { pasteInput = it },
                        placeholder = { Text("dakk://invite?data=... veya arkadaşının kodu", color = DakkTextTertiaryDark, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DakkYellow,
                            unfocusedBorderColor = DakkDarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val payload = SignalingPayload.decodeFromCompactString(pasteInput)
                            if (payload != null) {
                                if (payload.type == "OFFER") {
                                    viewModel.processRemoteOfferAndCreateAnswer(payload) {
                                        Toast.makeText(context, "${payload.alias} kişisi eklendi!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                } else if (payload.type == "ANSWER") {
                                    viewModel.applyRemoteAnswer(payload)
                                    Toast.makeText(context, "Bağlantı kuruldu!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } else {
                                Toast.makeText(context, "Geçersiz davet bağlantısı", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sohbeti Başlat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                "PIN" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { activeView = "MENU" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                        Text(
                            text = "6 Haneli Hızlı PIN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Arkadaşınızla belirlediğiniz 6 haneli kodu yazın:",
                        fontSize = 13.sp,
                        color = DakkTextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        placeholder = { Text("Örn: 948210", color = DakkTextTertiaryDark) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DakkYellow,
                            unfocusedBorderColor = DakkDarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (pinInput.length >= 4) {
                                    viewModel.createOfferForHandshake()
                                    viewModel.listenToPinRoom("${pinInput}_ans") { ans ->
                                        viewModel.applyRemoteAnswer(ans)
                                        Toast.makeText(context, "Bağlandı!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                    Toast.makeText(context, "Oda açıldı! Karşı taraf bağlanıyor...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Oda Aç", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (pinInput.length >= 4) {
                                    viewModel.listenToPinRoom(pinInput) { offer ->
                                        viewModel.processRemoteOfferAndCreateAnswer(offer) { ans ->
                                            viewModel.publishToPinRoom("${pinInput}_ans", ans) {
                                                Toast.makeText(context, "Bağlantı kuruldu!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        }
                                    }
                                    Toast.makeText(context, "Odaya bağlanılıyor...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DakkOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Katıl", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun QuickActionRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color = Color.White,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = DakkDarkSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = DakkTextSecondaryDark)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DakkTextTertiaryDark)
        }
    }
}
