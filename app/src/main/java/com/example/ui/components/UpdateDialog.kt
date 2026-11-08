package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DakkYellow
import com.example.update.ReleaseInfo
import com.example.util.ReleaseNotes

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    downloading: Boolean,
    progress: Int,
    signatureMismatch: Boolean,
    onUpdate: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit
) {
    if (signatureMismatch) {
        ReinstallRequiredDialog(release, onOpenReleasePage, onDismiss)
        return
    }

    val blocks = remember(release.notes) { ReleaseNotes.parse(release.notes) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = DakkYellow) },
        title = { Text("Yeni sürüm: v${release.version}") },
        text = {
            Column {
                Text(
                    "Kullandığınız sürüm: v$currentVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (blocks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        blocks.forEach { block -> NoteBlock(block) }
                    }
                }
                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = DakkYellow
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("İndiriliyor… %$progress", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
            ) {
                Text(if (downloading) "İndiriliyor…" else "Şimdi güncelle")
            }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = onDismiss) { Text("Daha sonra", color = DakkYellow) }
        }
    )
}

@Composable
private fun NoteBlock(block: ReleaseNotes.Block) {
    when (block) {
        is ReleaseNotes.Block.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DakkYellow,
            modifier = Modifier.padding(top = 4.dp)
        )
        is ReleaseNotes.Block.Bullet -> Row {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = DakkYellow)
            Spacer(Modifier.width(8.dp))
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is ReleaseNotes.Block.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReinstallRequiredDialog(
    release: ReleaseInfo,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = DakkYellow) },
        title = { Text("Yeniden kurulum gerekiyor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Dakk v${release.version}, güvenlik için yeni bir imza anahtarıyla yayınlandı. " +
                        "Android bu nedenle güncellemeyi mevcut uygulamanın üzerine kuramıyor.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Yapmanız gerekenler:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("1. Aşağıdaki düğmeyle yeni sürümün APK dosyasını indirin.", style = MaterialTheme.typography.bodyMedium)
                Text("2. Mevcut Dakk uygulamasını kaldırın.", style = MaterialTheme.typography.bodyMedium)
                Text("3. İndirdiğiniz APK'yı açıp kurun ve Google ile tekrar giriş yapın.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Bu işlem yalnızca bir kez gereklidir. Kişileriniz hesabınızda saklı kalır; " +
                        "şifreleme anahtarları yenileneceği için eski mesajlar okunamaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenReleasePage,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
            ) { Text("APK'yı indir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = DakkYellow) } }
    )
}
