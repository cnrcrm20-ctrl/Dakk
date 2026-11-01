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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.github.GitHubUpdateManager
import com.example.signaling.QrCodeHelper
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    viewModel: DakkViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showIdQrDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }

    var aliasInput by remember { mutableStateOf(viewModel.crypto.getUserAlias()) }

    val userId = remember { viewModel.crypto.getOrCreateUserId() }
    val blockedUsers by viewModel.blockedContacts.collectAsStateWithLifecycle()

    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.updateChecking.collectAsStateWithLifecycle()
    val updateStatusMsg by viewModel.updateStatusMessage.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DakkDarkBg)
            .padding(16.dp)
            .testTag("profile_screen_root")
    ) {
        item {
            // WhatsApp Profil Kartı
            Surface(
                color = DakkDarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAliasDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.dakk_logo),
                            contentDescription = "Dakk Maskot",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.crypto.getUserAlias(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dakk kullanıyor 🐥",
                            fontSize = 13.sp,
                            color = DakkTextSecondaryDark
                        )
                    }

                    IconButton(onClick = { showIdQrDialog = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR Kodum", tint = DakkYellow)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // WhatsApp Ayarlar Menüsü Listesi
            SettingsSectionHeader(title = "HESAP VE GİZLİLİK")

            SettingsRow(
                icon = Icons.Default.Security,
                iconBg = Color(0xFF10B981),
                title = "Uçtan Uca Şifreleme",
                subtitle = "Mesajlarınız AES-256 ile yalnızca cihazınızda şifrelenir",
                onClick = {
                    Toast.makeText(context, "Dakk'ta tüm mesajlar %100 uçtan uca şifrelidir", Toast.LENGTH_SHORT).show()
                }
            )

            SettingsRow(
                icon = Icons.Default.Key,
                iconBg = DakkOrange,
                title = "Kriptografik Dakk ID",
                subtitle = "512 karakterlik kimliği görüntüle veya QR olarak paylaş",
                onClick = { showIdQrDialog = true }
            )

            SettingsRow(
                icon = Icons.Default.Block,
                iconBg = DakkStatusFailed,
                title = "Engellenen Kişiler",
                subtitle = "${blockedUsers.size} kişi engellendi",
                onClick = {}
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader(title = "AĞ VE BAĞLANTI")

            SettingsRow(
                icon = Icons.Default.NetworkCheck,
                iconBg = Color(0xFF3B82F6),
                title = "Ağ & STUN Durumu",
                subtitle = "Operatör, ICE ve P2P bağlantı metrikleri",
                onClick = { showNetworkDialog = true }
            )

            SettingsRow(
                icon = Icons.Default.Share,
                iconBg = Color(0xFF25D366),
                title = "Arkadaşına WhatsApp'tan Öner",
                subtitle = "WhatsApp'tan arkadaşlarına Dakk daveti gönder",
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "👋 Selam! Dakk ile WhatsApp yerine doğrudan cihazdan cihaza (P2P), reklamsız ve sunucusuz mesajlaşalım! 🐥\n\nUygulamayı indir:\nhttps://github.com/cnrcrm20-ctrl/Dakk/releases/download/v1.0.1/app-debug.apk"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "WhatsApp ile Davet Et"))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader(title = "HAKKINDA VE GÜNCELLEMELER")

            SettingsRow(
                icon = Icons.Default.CloudSync,
                iconBg = DakkYellow,
                iconTint = Color.Black,
                title = "Sürüm Denetimi (GitHub)",
                subtitle = updateStatusMsg ?: "v${GitHubUpdateManager.CURRENT_VERSION} yüklü",
                onClick = { viewModel.checkGitHubUpdates() }
            )

            if (updateInfo?.apkDownloadUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.updateManager.downloadAndPromptInstall(
                            downloadUrl = updateInfo!!.apkDownloadUrl!!,
                            onProgress = {},
                            onComplete = {
                                Toast.makeText(context, "İndirme bitti, kuruluyor...", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DakkStatusConnected),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yeni Sürümü İndir ve Kur", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Kimliği Sıfırla
            Surface(
                color = DakkDarkSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResetDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DakkStatusFailed)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Kimliğimi ve Sohbetlerimi Sıfırla",
                        color = DakkStatusFailed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (blockedUsers.isNotEmpty()) {
            items(blockedUsers, key = { it.userId }) { blocked ->
                Surface(
                    color = DakkDarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = blocked.alias, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Engellendi", fontSize = 11.sp, color = DakkStatusFailed)
                        }

                        TextButton(
                            onClick = { viewModel.toggleBlock(blocked.userId, true) },
                            colors = ButtonDefaults.textButtonColors(contentColor = DakkYellow)
                        ) {
                            Text("Engeli Kaldır")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    // İsim Değiştirme Dialog'u
    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text("Adınızı Değiştirin", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DakkYellow,
                        unfocusedBorderColor = DakkDarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.crypto.setUserAlias(aliasInput)
                        showAliasDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DakkYellow)
                ) {
                    Text("Kaydet", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) {
                    Text("İptal", color = Color.LightGray)
                }
            },
            containerColor = DakkDarkSurface
        )
    }

    // QR Kod Dialog'u
    if (showIdQrDialog) {
        val idQrBitmap = remember(userId) {
            QrCodeHelper.generateQrBitmap(userId, 400, 400)
        }
        Dialog(onDismissRequest = { showIdQrDialog = false }) {
            Surface(
                color = DakkDarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Dakk QR Kodum",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = idQrBitmap.asImageBitmap(),
                            contentDescription = "Dakk QR",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Arkadaşınız bu kodu okutarak sizi hemen WhatsApp gibi ekleyebilir.",
                        fontSize = 12.sp,
                        color = DakkTextSecondaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showIdQrDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kapat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Ağ Durumu Popup'ı
    if (showNetworkDialog) {
        Dialog(onDismissRequest = { showNetworkDialog = false }) {
            Surface(
                color = DakkDarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Ağ ve Bağlantı Durumu", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• STUN Sunucusu: stun.l.google.com (Aktif)", color = DakkTextSecondaryDark, fontSize = 13.sp)
                    Text("• TURN Rölesi: Yok (0 TL Kuralı)", color = DakkTextSecondaryDark, fontSize = 13.sp)
                    Text("• Ağ Türü: ${viewModel.webRtc.detectNetworkType()}", color = DakkTextSecondaryDark, fontSize = 13.sp)
                    Text("• Operatör: ${viewModel.webRtc.selectedOperator.value}", color = DakkTextSecondaryDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showNetworkDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tamam", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Sıfırlama Onay Dialog'u
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Hesabı Sıfırla?", color = DakkStatusFailed) },
            text = {
                Text(
                    "Tüm sohbet geçmişiniz ve kriptografik anahtarlarınız yerel olarak silinecektir.",
                    color = Color.White,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.crypto.resetIdentity()
                        showResetDialog = false
                        Toast.makeText(context, "Sıfırlandı!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DakkStatusFailed)
                ) {
                    Text("Sıfırla", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("İptal", color = Color.LightGray)
                }
            },
            containerColor = DakkDarkSurface
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = DakkTextTertiaryDark,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color = Color.White,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = DakkDarkSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
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
