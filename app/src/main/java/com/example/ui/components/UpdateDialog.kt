package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.update.ReleaseInfo

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    downloading: Boolean,
    progress: Int,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("Yeni sürüm: v${release.version}") },
        text = {
            Column {
                Text(
                    "Kullandığınız sürüm: v$currentVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val notes = cleanNotes(release.notes)
                if (notes.isNotBlank()) {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        Text(notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("İndiriliyor… %$progress", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !downloading) {
                Text(if (downloading) "İndiriliyor…" else "Şimdi güncelle")
            }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = onDismiss) { Text("Daha sonra") }
        }
    )
}

/** GitHub sürüm notlarındaki temel Markdown işaretlerini sadeleştirir. */
private fun cleanNotes(raw: String): String =
    raw.lines()
        .filterNot { it.trim().startsWith("**Full Changelog**") || it.trim().startsWith("<!--") }
        .joinToString("\n") { line ->
            line.replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace("**", "")
                .replace("`", "")
        }
        .trim()
