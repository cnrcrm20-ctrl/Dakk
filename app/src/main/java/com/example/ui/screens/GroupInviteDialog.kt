package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DakkViewModel
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow

/** Grup davet bağlantısıyla açıldığında katılım onayı. */
@Composable
fun GroupInviteDialog(viewModel: DakkViewModel) {
    val invite by viewModel.groupInvite.collectAsStateWithLifecycle()
    val joining by viewModel.joiningGroup.collectAsStateWithLifecycle()
    val current = invite ?: return
    AlertDialog(
        onDismissRequest = { if (!joining) viewModel.dismissGroupInvite() },
        containerColor = DakkDarkSurface,
        icon = {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DakkYellow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Groups, null, tint = DakkYellow, modifier = Modifier.size(34.dp)) }
        },
        title = { Text("Grup daveti", textAlign = TextAlign.Center) },
        text = {
            Text(
                "Bir Dakk grubuna davet edildin. Katılırsan grup üyeleri adını ve profil fotoğrafını görür; " +
                    "katıldıktan sonraki mesajları okuyabilirsin.",
                color = DakkTextSecondaryDark,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = { viewModel.joinGroup(current) },
                enabled = !joining,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
            ) {
                if (joining) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Text("Gruba katıl")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissGroupInvite() }, enabled = !joining) { Text("Vazgeç") }
        }
    )
}
