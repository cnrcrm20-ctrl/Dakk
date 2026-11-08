package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthFailure
import com.example.auth.GoogleAuth
import com.example.auth.SignInResult
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow
import kotlinx.coroutines.launch

const val PRIVACY_POLICY_URL = "https://github.com/cnrcrm20-ctrl/Dakk/blob/main/PRIVACY_POLICY.md"

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AuthFailure?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DakkDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.dp, DakkYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.dakk_logo),
                    contentDescription = "Dakk",
                    modifier = Modifier.size(92.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Dakk",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = DakkYellow
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Arkadaşlarınızla güvenle mesajlaşın.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
            Feature(Icons.Default.Lock, "Uçtan uca şifreli", "Mesajlarınızı yalnızca siz ve karşınızdaki kişi okuyabilir.")
            Spacer(Modifier.height(10.dp))
            Feature(Icons.Default.QrCode2, "Kolay kişi ekleme", "QR kod, davet bağlantısı veya e-posta ile saniyeler içinde ekleyin.")
            Spacer(Modifier.height(10.dp))
            Feature(Icons.Default.MarkChatRead, "Her yerde senkron", "Mesajlarınız çevrimdışıyken bile iletilmek üzere sıraya alınır.")

            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(visible = failure != null) {
                val f = failure
                if (f != null) {
                    Surface(
                        color = Color(0xFF3A1717),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF8A80))
                                Spacer(Modifier.width(10.dp))
                                Text(f.message, color = Color(0xFFFFDAD6), style = MaterialTheme.typography.bodyMedium)
                            }
                            if (f.technicalDetails != null) {
                                TextButton(onClick = { showDetails = !showDetails }) {
                                    Text(
                                        if (showDetails) "Teknik ayrıntıları gizle" else "Teknik ayrıntılar",
                                        color = Color(0xFFFFB4AB)
                                    )
                                }
                                if (showDetails) {
                                    Text(
                                        f.technicalDetails,
                                        color = Color(0xFFFFDAD6),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .clickable { copyToClipboard(context, f.technicalDetails) }
                                            .padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (loading) return@Button
                    loading = true
                    failure = null
                    showDetails = false
                    scope.launch {
                        when (val result = GoogleAuth.signIn(context)) {
                            SignInResult.Success -> Unit // Oturum dinleyicisi ana ekrana geçirir.
                            SignInResult.Cancelled -> Unit
                            is SignInResult.Failed -> failure = result.failure
                        }
                        loading = false
                    }
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Giriş yapılıyor…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                } else {
                    Text("Google ile devam et", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Devam ederek Gizlilik Politikası'nı kabul etmiş olursunuz.",
                style = MaterialTheme.typography.bodySmall,
                color = DakkTextSecondaryDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                    }
                }
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DakkDarkSurfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DakkYellow.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DakkYellow, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = DakkTextSecondaryDark, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Dakk") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
}
