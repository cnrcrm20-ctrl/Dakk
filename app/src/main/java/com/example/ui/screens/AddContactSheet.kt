package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DakkViewModel
import com.example.ui.components.Avatar
import com.example.ui.components.CameraQrScannerView
import com.example.ui.components.QrCodeDialog
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.QrCodeHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactSheet(viewModel: DakkViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.addContact.collectAsStateWithLifecycle()
    val me by viewModel.myProfile.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showMyQr by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner = true
        else Toast.makeText(context, "QR kod taramak için kamera izni gerekiyor.", Toast.LENGTH_LONG).show()
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val decoded = QrCodeHelper.decode(context, uri)
            if (decoded != null) viewModel.lookupContact(decoded)
            else Toast.makeText(context, "Seçilen görselde Dakk QR kodu bulunamadı.", Toast.LENGTH_LONG).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.resetAddContact()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = DakkDarkSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp)
        ) {
            Text("Kişi ekle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))

            ActionRow(Icons.Default.QrCodeScanner, "QR kod tara", "Arkadaşınızın ekranındaki kodu okutun") {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    showScanner = true
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            ActionRow(Icons.Default.Image, "Galeriden QR seç", "Ekran görüntüsündeki QR kodu tanıyın") {
                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ActionRow(Icons.Default.QrCode2, "QR kodumu göster", "Arkadaşınız sizi tarayarak eklesin") {
                showMyQr = true
            }
            ActionRow(Icons.Default.Share, "Davet bağlantısı paylaş", "WhatsApp, SMS veya istediğiniz uygulama ile") {
                viewModel.myInviteLink()?.let { shareInvite(context, it) }
            }

            Spacer(Modifier.height(16.dp))
            Text("E-posta veya davet bağlantısı ile", color = DakkTextSecondaryDark, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("ornek@gmail.com veya bağlantı") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.lookupContact(input) }),
                isError = state.error != null && state.found == null,
                supportingText = if (state.error != null && state.found == null) {
                    { Text(state.error ?: "") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.lookupContact(input) },
                enabled = input.isNotBlank() && !state.loading,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.loading) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Ara", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize()) {
                CameraQrScannerView(
                    onQrCodeScanned = { raw ->
                        showScanner = false
                        viewModel.lookupContact(raw)
                    },
                    onClose = { showScanner = false }
                )
            }
        }
    }

    if (showMyQr) {
        val link = viewModel.myInviteLink()
        if (link != null) {
            QrCodeDialog(
                name = me?.displayName ?: "",
                link = link,
                onShare = { shareInvite(context, link) },
                onCopy = { copyToClipboard(context, link, "Dakk davet bağlantısı") },
                onDismiss = { showMyQr = false }
            )
        }
    }
}

/** Bulunan kişiyi onaylama penceresi (QR, bağlantı veya e-posta araması sonrası). */
@Composable
fun FoundContactDialog(viewModel: DakkViewModel) {
    val state by viewModel.addContact.collectAsStateWithLifecycle()
    val profile = state.found ?: return
    AlertDialog(
        onDismissRequest = { viewModel.resetAddContact() },
        icon = { Avatar(profile.displayName, profile.photoUrl, size = 64.dp) },
        title = { Text(profile.displayName.ifBlank { "Dakk Kullanıcısı" }) },
        text = {
            Column {
                if (profile.email.isNotBlank()) Text(profile.email, color = DakkTextSecondaryDark)
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.confirmAddContact(profile) },
                enabled = !state.loading,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
            ) { Text(if (viewModel.isContact(profile.uid)) "Mesaj gönder" else "Ekle ve mesaj gönder") }
        },
        dismissButton = { TextButton(onClick = { viewModel.resetAddContact() }) { Text("Vazgeç") } }
    )
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DakkDarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DakkYellow.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DakkYellow)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DakkTextSecondaryDark, fontSize = 13.sp)
        }
    }
}

fun shareInvite(context: Context, link: String) {
    val text = "Dakk'ta benimle uçtan uca şifreli mesajlaş! Beni eklemek için:\n$link\n\n" +
        "Dakk'ı indir: https://github.com/cnrcrm20-ctrl/Dakk/releases/latest"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Davet et"))
}
