package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Otomatik sessiz giriş dene (eğer daha önceden yetkilendirilmiş hesap varsa)
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Kullanıcı etkileşimli butona basacak */ },
            scope = coroutineScope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DakkDarkBg,
                        DakkDarkSurface,
                        Color(0xFF13151B)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Dakk Ördek Logosu
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(DakkYellow.copy(alpha = 0.15f))
                    .border(2.dp, DakkYellow.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dakk_logo),
                    contentDescription = "Dakk Logo",
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Dakk Messenger",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Doğrudan Cihazdan Cihaza (P2P) & Bulut Destekli Güvenli İletişim",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = DakkTextSecondaryDark,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Özellik Kartları
            FeaturePill(
                icon = Icons.Default.Lock,
                title = "Uçtan Uca Şifreli (E2EE)",
                subtitle = "Mesajlarınız cihazınızda şifrelenir"
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeaturePill(
                icon = Icons.Default.Sync,
                title = "Google Hesabıyla Oturum",
                subtitle = "Profiliniz ve kişileriniz anında eşleşir"
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeaturePill(
                icon = Icons.Default.QrCode,
                title = "Hızlı QR & PIN Eşleşmesi",
                subtitle = "Tek dokunuşla arkadaşlarını hemen ekle"
            )

            Spacer(modifier = Modifier.height(36.dp))

            errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Google Sign-In Butonu
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            Log.w("AuthScreen", "Google Sign-In failed, attempting fallback: $err")
                            // Fallback to anonymous auth if Google Sign-in fails on device
                            Firebase.auth.signInAnonymously()
                                .addOnSuccessListener {
                                    isLoading = false
                                    onAuthSuccess()
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    errorMessage = "Giriş başarısız: ${e.localizedMessage}"
                                }
                        },
                        scope = coroutineScope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DakkYellow,
                    contentColor = DakkDarkBg
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DakkDarkBg,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Google ile Giriş Yap 🐥",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DakkDarkBg
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Misafir / Hızlı Giriş Butonu (Yedek)
            TextButton(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    Firebase.auth.signInAnonymously()
                        .addOnSuccessListener {
                            isLoading = false
                            onAuthSuccess()
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            errorMessage = "Misafir girişi başarısız: ${e.localizedMessage}"
                        }
                },
                enabled = !isLoading
            ) {
                Text(
                    text = "Veya Misafir Olarak Giriş Yap 🚀",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = DakkDarkSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DakkYellow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DakkYellow,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = DakkTextSecondaryDark
                    )
                )
            }
        }
    }
}

private const val DEFAULT_FALLBACK_CLIENT_ID = "721295735056-2b9hjum4qc2t4pjm530cn31fahtdg31j.apps.googleusercontent.com"

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        val id = context.getString(R.string.default_web_client_id)
        if (id.isBlank()) DEFAULT_FALLBACK_CLIENT_ID else id
    } catch (e: Exception) {
        DEFAULT_FALLBACK_CLIENT_ID
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        val id = context.getString(R.string.default_web_client_id)
        if (id.isBlank()) DEFAULT_FALLBACK_CLIENT_ID else id
    } catch (e: Exception) {
        DEFAULT_FALLBACK_CLIENT_ID
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Beklenmeyen kimlik türü")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed: ${e.message}", e)
            onAuthError(e.localizedMessage ?: "Giriş yapılamadı")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
