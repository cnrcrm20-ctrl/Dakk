package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaling.QrCodeHelper
import com.example.signaling.SignalingPayload
import com.example.ui.DakkViewModel
import com.example.ui.theme.*
import org.webrtc.PeerConnection

@Composable
fun HandshakeScreen(
    viewModel: DakkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableStateOf(0) }

    val myOffer by viewModel.myOfferPayload.collectAsStateWithLifecycle()
    val myAnswer by viewModel.myAnswerPayload.collectAsStateWithLifecycle()
    val handshakeStatus by viewModel.handshakeStatus.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingOffer.collectAsStateWithLifecycle()
    val iceState by viewModel.webRtc.iceConnectionState.collectAsStateWithLifecycle()

    var pastedData by remember { mutableStateOf("") }
    var pinCodeInput by remember { mutableStateOf("") }
    var rendezvousLog by remember { mutableStateOf("PIN belirleyip eşleşebilirsiniz.") }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DakkDarkBg)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .testTag("handshake_screen_root")
    ) {
        Text(
            text = "P2P SİNYALLEŞME & EŞLEŞME",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = DakkYellow,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Sıfır sunucu ve 0 TL maliyetle SDP/ICE alışverişi yapın.",
            fontSize = 13.sp,
            color = DakkTextSecondaryDark
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Alt Sekmeler
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DakkDarkSurface,
            contentColor = DakkYellow
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("QR Kod", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Bağlantı", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("6-Haneli PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Canlı Durum Kartı
        Card(
            colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = if (iceState == PeerConnection.IceConnectionState.CONNECTED) DakkStatusConnected else DakkYellow
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Bağlantı Durumu: ${iceState.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = handshakeStatus,
                        fontSize = 12.sp,
                        color = DakkTextSecondaryDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedSubTab) {
            0 -> {
                // QR Kod İle Eşleşme
                Card(
                    colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Adım 1: Teklif (Offer) QR Kodunu Gösterin",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (myOffer != null) {
                            val qrBitmap = remember(myOffer) {
                                val compact = myOffer!!.encodeToCompactString()
                                QrCodeHelper.generateQrBitmap(compact, 450, 450)
                            }

                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "P2P Handshake QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Dakk Davet", myOffer!!.toInvitationLink())
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Davet bağlantısı panoya kopyalandı", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DakkYellow)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Kopyala")
                                }

                                Button(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Dakk üzerinden doğrudan P2P mesajlaşmak için davet bağlantım:\n${myOffer!!.toInvitationLink()}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Dakk Davetini Paylaş"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DakkYellow)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paylaş", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.createOfferForHandshake() },
                                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                                enabled = !isGenerating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Adaylar Toplanıyor...", color = Color.Black)
                                } else {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Yeni Teklif (Offer) Üret", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = DakkDarkBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Adım 2: Karşı Tarafın Cevabını (Answer) Uygulayın",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = pastedData,
                            onValueChange = { pastedData = it },
                            placeholder = { Text("Karşıdan gelen Answer kodunu veya linkini yapıştırın...", fontSize = 12.sp, color = DakkTextTertiaryDark) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DakkYellow,
                                unfocusedBorderColor = DakkDarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val payload = SignalingPayload.decodeFromCompactString(pastedData)
                                if (payload != null && payload.type == "ANSWER") {
                                    viewModel.applyRemoteAnswer(payload)
                                    pastedData = ""
                                    Toast.makeText(context, "Cevap uygulandı, bağlantı kuruluyor!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Geçersiz Cevap (Answer) verisi!", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DakkOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cevabı Uygula & Bağlan", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            1 -> {
                // Bağlantı / Pano İle Eşleşme (Alıcı veya Cevaplayıcı taraf)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Gelen Davet Bağlantısını İşle",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Arkadaşınızın size WhatsApp veya Telegram üzerinden gönderdiği dakk:// davetini buraya yapıştırın.",
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = pastedData,
                            onValueChange = { pastedData = it },
                            placeholder = { Text("dakk://invite?data=...", fontSize = 12.sp, color = DakkTextTertiaryDark) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DakkYellow,
                                unfocusedBorderColor = DakkDarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val payload = SignalingPayload.decodeFromCompactString(pastedData)
                                if (payload != null) {
                                    if (payload.type == "OFFER") {
                                        viewModel.processRemoteOfferAndCreateAnswer(payload) { myAns ->
                                            Toast.makeText(context, "Cevap hazırlandı!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (payload.type == "ANSWER") {
                                        viewModel.applyRemoteAnswer(payload)
                                        Toast.makeText(context, "Cevap uygulandı!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Davet bağlantısı çözülemedi!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Daveti Çöz & Cevap (Answer) Üret", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        if (myAnswer != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = DakkDarkBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Üretilen Cevap Bağlantınız:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DakkOrangeLight
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Dakk P2P Cevap Bağlantım:\n${myAnswer!!.toInvitationLink()}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Cevabı Karşı Tarafa Gönder"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DakkOrange),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Reply, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cevap Bağlantısını Arkadaşına İlet", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            2 -> {
                // 6 Haneli PIN ile 0 TL Bulut Rendezvous
                Card(
                    colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "6 Haneli Oda Kodu ile Eşleş (0 TL)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "QR kod veya uzun linklerle uğraşmak istemiyorsanız, arkadaşınızla ortak 6 haneli bir PIN kodu (örn. 839210) belirleyin.",
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pinCodeInput,
                            onValueChange = { if (it.length <= 6) pinCodeInput = it },
                            placeholder = { Text("Örn: 839210", color = DakkTextTertiaryDark) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DakkYellow,
                                unfocusedBorderColor = DakkDarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // PIN ile Teklifi Yayınla
                            Button(
                                onClick = {
                                    if (pinCodeInput.length >= 4) {
                                        rendezvousLog = "PIN odasına teklif yükleniyor..."
                                        viewModel.createOfferForHandshake()
                                        // Dinlemeyi de başlat ki gelen cevabı alsın
                                        viewModel.listenToPinRoom("${pinCodeInput}_ans") { ansPayload ->
                                            if (ansPayload.type == "ANSWER") {
                                                viewModel.applyRemoteAnswer(ansPayload)
                                                rendezvousLog = "Karşı tarafın cevabı alındı! P2P bağlantı kuruluyor..."
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "En az 4 haneli PIN girin", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Oda Kur (Teklif)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // PIN ile Odaya Katıl
                            Button(
                                onClick = {
                                    if (pinCodeInput.length >= 4) {
                                        rendezvousLog = "Oda $pinCodeInput dinleniyor... Teklif bekleniyor..."
                                        viewModel.listenToPinRoom(pinCodeInput) { offerPayload ->
                                            if (offerPayload.type == "OFFER") {
                                                rendezvousLog = "Teklif alındı! Cevap üretilip odaya iletiliyor..."
                                                viewModel.processRemoteOfferAndCreateAnswer(offerPayload) { answerPayload ->
                                                    viewModel.publishToPinRoom("${pinCodeInput}_ans", answerPayload) { success ->
                                                        rendezvousLog = if (success) "Cevap gönderildi! P2P doğrudan bağlandı." else "Cevap iletim hatası."
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "En az 4 haneli PIN girin", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DakkOrange),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Odaya Katıl", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Offer hazır olduğunda otomatik odaya bas
                        LaunchedEffect(myOffer) {
                            if (myOffer != null && pinCodeInput.length >= 4) {
                                viewModel.publishToPinRoom(pinCodeInput, myOffer!!) {
                                    rendezvousLog = "Teklif odaya yüklendi! Karşı tarafın katılması bekleniyor..."
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            color = DakkDarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Canlı Günlük: $rendezvousLog",
                                fontSize = 12.sp,
                                color = DakkYellowLight,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
