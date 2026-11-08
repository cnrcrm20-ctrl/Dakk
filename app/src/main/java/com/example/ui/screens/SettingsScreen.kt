package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DakkViewModel
import com.example.ui.components.Avatar
import com.example.ui.components.QrCodeDialog
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkStatusFailed
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow

private const val REPO_URL = "https://github.com/cnrcrm20-ctrl/Dakk"

@Composable
fun SettingsScreen(viewModel: DakkViewModel) {
    val context = LocalContext.current
    val me by viewModel.myProfile.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedContacts.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()

    var showQr by remember { mutableStateOf(false) }
    var showEditName by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val name = me?.displayName?.ifBlank { null } ?: user?.displayName ?: "Dakk Kullanıcısı"
    val email = user?.email.orEmpty()
    val photo = me?.photoUrl?.ifBlank { null } ?: user?.photoUrl?.toString()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEditName = true }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name, photo, size = 68.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (email.isNotBlank()) Text(email, color = DakkTextSecondaryDark, fontSize = 14.sp)
            }
            Icon(Icons.Default.Edit, contentDescription = "Adı düzenle", tint = DakkTextSecondaryDark)
        }
        HorizontalDivider(color = DakkDarkBorder)

        SectionTitle("Hesap")
        SettingRow(Icons.Default.QrCode2, "QR kodum", "Arkadaşlarınız sizi tarayarak eklesin") { showQr = true }
        SettingRow(Icons.Default.Share, "Arkadaşlarını davet et", "Davet bağlantınızı paylaşın") {
            viewModel.myInviteLink()?.let { shareInvite(context, it) }
        }
        SettingRow(
            Icons.Default.Block,
            "Engellenen kişiler",
            if (blocked.isEmpty()) "Engellenen kişi yok" else "${blocked.size} kişi"
        ) { showBlocked = true }

        SectionTitle("Uygulama")
        SettingRow(Icons.Default.Notifications, "Bildirimler", "Bildirim ayarlarını aç") {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
            runCatching { context.startActivity(intent) }
        }
        SettingRow(
            Icons.Default.SystemUpdate,
            "Güncellemeleri denetle",
            update.statusMessage ?: "Sürüm ${viewModel.appVersion}"
        ) {
            if (update.release != null) viewModel.showUpdateDialog() else viewModel.checkForUpdates(manual = true)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAutoUpdate(!autoUpdate) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text("Otomatik güncelleme denetimi", color = Color.White, fontSize = 16.sp)
                Text("Açılışta yeni sürüm olup olmadığını kontrol et", color = DakkTextSecondaryDark, fontSize = 13.sp)
            }
            Switch(
                checked = autoUpdate,
                onCheckedChange = { viewModel.setAutoUpdate(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DakkYellow, checkedThumbColor = Color.Black)
            )
        }

        SectionTitle("Hakkında")
        SettingRow(Icons.Default.PrivacyTip, "Gizlilik Politikası", null) { openUrl(context, PRIVACY_POLICY_URL) }
        SettingRow(Icons.Default.Code, "Açık kaynak kodu", "GitHub'da görüntüle") { openUrl(context, REPO_URL) }
        SettingRow(Icons.Default.Info, "Dakk", "Sürüm ${viewModel.appVersion}") { openUrl(context, "$REPO_URL/releases") }

        Spacer(Modifier.height(8.dp))
        SettingRow(
            Icons.AutoMirrored.Filled.Logout,
            "Çıkış yap",
            null,
            tint = DakkStatusFailed
        ) { confirmSignOut = true }
        Spacer(Modifier.height(32.dp))
    }

    if (showQr) {
        val link = viewModel.myInviteLink()
        if (link != null) {
            QrCodeDialog(
                name = name,
                link = link,
                onShare = { shareInvite(context, link) },
                onCopy = { copyToClipboard(context, link, "Dakk davet bağlantısı") },
                onDismiss = { showQr = false }
            )
        }
    }

    if (showEditName) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { showEditName = false },
            title = { Text("Görünen ad") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 60) draft = it },
                    singleLine = true,
                    supportingText = { Text("Kişileriniz sizi bu adla görür") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(draft)
                        showEditName = false
                    },
                    enabled = draft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
                ) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showEditName = false }) { Text("Vazgeç") } }
        )
    }

    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            title = { Text("Engellenen kişiler") },
            text = {
                if (blocked.isEmpty()) {
                    Text("Engellediğiniz kimse yok.")
                } else {
                    Column {
                        blocked.forEach { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Avatar(c.alias, c.photoUrl, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(c.alias, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.setBlocked(c.uid, false) }) { Text("Kaldır") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlocked = false }) { Text("Kapat") } }
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Çıkış yapılsın mı?") },
            text = { Text("Tekrar giriş yaptığınızda sohbetleriniz geri gelir.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Çıkış yap", color = DakkStatusFailed) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = DakkTextTertiaryDark,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color = DakkYellow,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == DakkStatusFailed) tint else Color.White, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = DakkTextSecondaryDark, fontSize = 13.sp, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
