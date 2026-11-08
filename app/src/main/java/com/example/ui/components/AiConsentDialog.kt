package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AI_INFO_URL
import com.example.ui.screens.openUrl
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow

/**
 * Yapay zekâ kullanılmadan önce gösterilen bilgilendirme.
 * inChat = true: bir sohbette @dakk ile çağrıldığında (sohbetin son mesajları da işlenir).
 */
@Composable
fun AiConsentDialog(inChat: Boolean, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = DakkYellow) },
        title = { Text("Dakk Asistan") },
        text = {
            Column {
                Text(
                    "Bu mesaj ve sohbet yapay zekâ tarafından işlenecek.",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (inChat) {
                        "Yanıt üretebilmek için bu sohbetin son mesajları yapay zekâya gönderilir. " +
                            "Asistanın yanıtı sohbete eklenir ve karşı taraf da görür. " +
                            "Asistanın gördüğü mesajlar uçtan uca şifreleme kapsamı dışında kalır."
                    } else {
                        "Asistana yazdıklarınız yanıt üretmek için yapay zekâya gönderilir. " +
                            "Bu sohbet uçtan uca şifreli değildir; kişisel veya gizli bilgi paylaşmayın."
                    },
                    color = DakkTextSecondaryDark,
                    fontSize = 14.sp
                )
                TextButton(onClick = { openUrl(context, AI_INFO_URL) }) {
                    Text("Ayrıntılar", color = DakkYellow)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
            ) { Text("Devam et") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
