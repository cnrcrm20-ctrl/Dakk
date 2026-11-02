package com.example.ui.components

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.*

@Composable
fun PrivacyPolicyDialog(
    onAccepted: () -> Unit
) {
    val context = LocalContext.current

    // İzinler Listesi
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val prefs = context.getSharedPreferences("dakk_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_privacy_accepted", true).apply()
        Toast.makeText(context, "Dakk Gizlilik Politikası Kabul Edildi!", Toast.LENGTH_SHORT).show()
        onAccepted()
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DakkDarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(DakkYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.dakk_logo),
                        contentDescription = "Dakk Logo",
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Dakk Gizlilik İlkeleri 🐥",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tam Gizlilik, Sıfır İzleme, %100 Doğrudan P2P",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DakkYellow
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DakkDarkBg, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrivacyBullet(title = "Telefon veya E-posta İstemez", desc = "Kayıt olmanıza gerek yok. Sadece bir takma ad belirleyin.")
                    PrivacyBullet(title = "Merkezi Sunucu Yoktur", desc = "Mesajlar hiçbir sunucuda depolanmaz, doğrudan cihazlar arasında iletilir.")
                    PrivacyBullet(title = "Uçtan Uca Şifreli", desc = "Tüm yazışmalarınız ve sesli mesajlarınız P-256 / AES-256 ile şifrelenir.")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(permissionsToRequest)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DakkYellow)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kabul Et ve Tüm İzinleri Ver 🚀",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyBullet(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = DakkYellow, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = desc, fontSize = 11.sp, color = DakkTextSecondaryDark)
        }
    }
}
