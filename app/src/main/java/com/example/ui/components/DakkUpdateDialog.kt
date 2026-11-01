package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.github.ReleaseInfo
import com.example.ui.theme.*

@Composable
fun DakkUpdateDialog(
    releaseInfo: ReleaseInfo,
    downloadProgress: Int?,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismissClick,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300))
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DakkDarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sevimli Uykucu Ördek Görseli
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_update_duck),
                            contentDescription = "Dakk Güncelleme Ördeği",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Dakk Güncellemesi Var! 🐥",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Yeni Sürüm: v${releaseInfo.tagName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DakkYellow,
                        textAlign = TextAlign.Center
                    )

                    if (releaseInfo.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = releaseInfo.description,
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark,
                            textAlign = TextAlign.Center,
                            maxLines = 4
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "En yeni P2P bağlantı geliştirmeleri ve performans düzeltmeleri hazır.",
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isDownloading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { (downloadProgress ?: 0) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = DakkYellow,
                                trackColor = DakkDarkBorder,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "İndiriliyor: %${downloadProgress ?: 0}",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismissClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Ertele", fontSize = 14.sp, color = DakkTextSecondaryDark)
                            }

                            Button(
                                onClick = {
                                    if (releaseInfo.apkDownloadUrl != null) {
                                        onDownloadClick()
                                    } else {
                                        // Web browser fallback
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/cnrcrm20-ctrl/Dakk/releases/latest")
                                        )
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow)
                            ) {
                                Text(
                                    text = "Şimdi İndir 🚀",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
