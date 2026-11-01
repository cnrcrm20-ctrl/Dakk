package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.NetworkLogEntity
import com.example.ui.DakkViewModel
import com.example.ui.theme.*
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkDiagnosticScreen(
    viewModel: DakkViewModel,
    modifier: Modifier = Modifier
) {
    val selectedOp by viewModel.webRtc.selectedOperator.collectAsStateWithLifecycle()
    val detectedNetType by viewModel.webRtc.networkType.collectAsStateWithLifecycle()
    val iceState by viewModel.webRtc.iceConnectionState.collectAsStateWithLifecycle()
    val dcState by viewModel.webRtc.dataChannelState.collectAsStateWithLifecycle()
    val stunLatency by viewModel.webRtc.stunLatencyMs.collectAsStateWithLifecycle()
    val p2pRtt by viewModel.webRtc.p2pRttMs.collectAsStateWithLifecycle()
    val candidateTypes by viewModel.webRtc.candidateTypes.collectAsStateWithLifecycle()
    val networkLogs by viewModel.networkLogs.collectAsStateWithLifecycle()

    val operators = listOf("Turkcell", "Vodafone", "Türk Telekom", "BİMcell", "Netgsm", "Wi-Fi (Ev/Ofis)", "Otomatik")
    var testNoteInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DakkDarkBg)
            .padding(16.dp)
            .testTag("diagnostic_screen_root")
    ) {
        item {
            Text(
                text = "TÜRKİYE OPERATÖRLERİ & P2P TEST LABORATUVARI",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = DakkYellow,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Farklı operatör ve ağlar arasında doğrudan P2P STUN ve DataChannel performansı test edilir. IP adresi kaydedilmez.",
                fontSize = 12.sp,
                color = DakkTextSecondaryDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Operatör Seçici
            Card(
                colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Aktif Operatör / Ağ Seçimi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        operators.forEach { op ->
                            val isSelected = selectedOp == op
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.webRtc.setSelectedOperator(op) },
                                label = { Text(op, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DakkYellow,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DakkDarkSurfaceVariant,
                                    labelColor = DakkTextSecondaryDark
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tespit Edilen Fiziksel Ağ: $detectedNetType",
                        fontSize = 12.sp,
                        color = DakkTextTertiaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gerçek Zamanlı Telemetri Kartı
            Card(
                colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Canlı Ağ & ICE Metrikleri",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        TextButton(
                            onClick = { viewModel.webRtc.measureStunLatency() },
                            colors = ButtonDefaults.textButtonColors(contentColor = DakkYellow)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STUN Ping", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metrik Satırları
                    MetricRow(
                        label = "Google STUN Yanıtı",
                        value = if (stunLatency > 0) "${stunLatency} ms" else "Yanıt Bekleniyor / Başarısız",
                        color = if (stunLatency > 0) DakkStatusConnected else DakkStatusFailed
                    )

                    MetricRow(
                        label = "ICE Bağlantı Durumu",
                        value = iceState.name,
                        color = when (iceState) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> DakkStatusConnected
                            PeerConnection.IceConnectionState.CHECKING -> DakkStatusConnecting
                            else -> DakkStatusFailed
                        }
                    )

                    MetricRow(
                        label = "WebRTC DataChannel",
                        value = dcState.name,
                        color = if (dcState == DataChannel.State.OPEN) DakkStatusConnected else DakkStatusFailed
                    )

                    MetricRow(
                        label = "P2P Paket Gecikmesi (RTT)",
                        value = if (p2pRtt > 0) "$p2pRtt ms" else "P2P Kanal Kapalı",
                        color = if (p2pRtt > 0) DakkStatusConnected else DakkTextSecondaryDark
                    )

                    MetricRow(
                        label = "Toplanan ICE Aday Türleri",
                        value = if (candidateTypes.isNotEmpty()) candidateTypes.joinToString(", ") else "Aday Yok (STUN başlatın)",
                        color = DakkYellowLight
                    )

                    MetricRow(
                        label = "TURN / Röle Kullanımı",
                        value = "KESİNLİKLE YOK (0 TL Kuralı)",
                        color = DakkOrangeLight
                    )

                    MetricRow(
                        label = "Bluetooth Kullanımı",
                        value = "KESİNLİKLE YOK (Saf IP P2P)",
                        color = DakkOrangeLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Kaydı Ekleme
            Card(
                colors = CardDefaults.cardColors(containerColor = DakkDarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Yerel Cihaz Test Günlüğü Kaydet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = testNoteInput,
                        onValueChange = { testNoteInput = it },
                        placeholder = { Text("Örn: Turkcell 4.5G ile Vodafone Wi-Fi arası el sıkışma başarılı", fontSize = 12.sp, color = DakkTextTertiaryDark) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DakkYellow,
                            unfocusedBorderColor = DakkDarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val notes = if (testNoteInput.isNotBlank()) testNoteInput else "Standart Teşhis Kaydı"
                            viewModel.logDiagnosticResult(notes)
                            testNoteInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sonucu Yerel Hafızaya Kaydet", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KAYITLI TEST SONUÇLARI (${networkLogs.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DakkTextTertiaryDark,
                    letterSpacing = 1.sp
                )

                if (networkLogs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            // viewModel.repository.clearNetworkLogs()
                        }
                    ) {
                        Text("Temizle", fontSize = 11.sp, color = DakkTextTertiaryDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        items(networkLogs, key = { it.id }) { log ->
            NetworkLogItem(log = log)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = DakkTextSecondaryDark)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun NetworkLogItem(log: NetworkLogEntity) {
    val sdf = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
    Surface(
        color = DakkDarkSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${log.operatorName} • ${log.networkType}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DakkYellow
                )
                Text(
                    text = sdf.format(Date(log.timestamp)),
                    fontSize = 10.sp,
                    color = DakkTextTertiaryDark
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "ICE: ${log.iceState} | STUN: ${log.stunLatencyMs}ms | RTT: ${log.rttMs}ms",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            if (log.statusSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.statusSummary,
                    fontSize = 11.sp,
                    color = DakkTextSecondaryDark
                )
            }
        }
    }
}
