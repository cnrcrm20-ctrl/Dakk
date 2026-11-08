package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.util.AppSignature
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await
import java.io.IOException

/** Kullanıcıya gösterilecek giriş hatası. */
data class AuthFailure(
    val message: String,
    val technicalDetails: String? = null
)

sealed class SignInResult {
    object Success : SignInResult()
    object Cancelled : SignInResult()
    data class Failed(val failure: AuthFailure) : SignInResult()
}

object GoogleAuth {

    private const val TAG = "DakkAuth"

    /** Google hesabı seçiciyi açar ve Firebase oturumu başlatır. [activityContext] bir Activity olmalıdır. */
    suspend fun signIn(activityContext: Context): SignInResult {
        val credentialManager = CredentialManager.create(activityContext)
        val option = GetSignInWithGoogleOption.Builder(
            activityContext.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = credentialManager.getCredential(activityContext, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return SignInResult.Failed(AuthFailure("Beklenmeyen hesap türü. Lütfen tekrar deneyin."))
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val result = Firebase.auth.signInWithCredential(firebaseCredential).await()
            if (result.user != null) SignInResult.Success
            else SignInResult.Failed(AuthFailure("Oturum açılamadı. Lütfen tekrar deneyin."))
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Google ile giriş başarısız", e)
            SignInResult.Failed(describe(activityContext, e))
        }
    }

    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Kimlik bilgisi durumu temizlenemedi", e)
        }
        Firebase.auth.signOut()
    }

    private fun describe(context: Context, e: Exception): AuthFailure {
        val raw = e.message.orEmpty()
        val isDeveloperError = raw.contains("28444") ||
            raw.contains("Developer console", ignoreCase = true) ||
            Regex("""(^|\D)10:""").containsMatchIn(raw)

        return when {
            isDeveloperError -> {
                val sha1 = AppSignature.fingerprint(context, "SHA-1") ?: "bilinmiyor"
                AuthFailure(
                    message = "Google ile giriş bu uygulama sürümü için henüz etkinleştirilmemiş. " +
                        "Lütfen kısa bir süre sonra tekrar deneyin veya uygulamayı güncelleyin.",
                    technicalDetails = "Hata 10 (DEVELOPER_ERROR)\n" +
                        "Paket: ${context.packageName}\n" +
                        "İmza SHA-1: $sha1\n" +
                        "Bu SHA-1 değeri Firebase Console › Proje ayarları › Android uygulaması bölümüne eklenmelidir."
                )
            }
            e is NoCredentialException -> AuthFailure(
                "Bu cihazda kullanılabilir bir Google hesabı bulunamadı. " +
                    "Ayarlar › Hesaplar bölümünden bir Google hesabı ekleyip tekrar deneyin."
            )
            e is FirebaseNetworkException || e is IOException || raw.contains("network", ignoreCase = true) ->
                AuthFailure("İnternet bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin.")
            e is FirebaseAuthException -> AuthFailure(
                "Google hesabınız doğrulanamadı. Lütfen tekrar deneyin.",
                technicalDetails = "${e.errorCode}: $raw"
            )
            e is GetCredentialException -> AuthFailure(
                "Google ile giriş başlatılamadı. Google Play Hizmetleri'nin güncel olduğundan emin olun.",
                technicalDetails = "${e.type}: $raw"
            )
            else -> AuthFailure(
                "Giriş sırasında beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.",
                technicalDetails = raw.ifBlank { e.javaClass.simpleName }
            )
        }
    }
}
