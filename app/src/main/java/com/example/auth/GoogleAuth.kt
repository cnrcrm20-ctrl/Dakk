package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.example.util.AppSignature
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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

    /**
     * Google hesabı seçiciyi açar ve Firebase oturumu başlatır. [activityContext] bir Activity olmalıdır.
     *
     * Önce "Google ile oturum aç" düğme akışı denenir. Bu akış bazı cihazlarda (ör. MIUI) veya
     * yapılandırma sorunlarında hesap seçildikten sonra sessizce iptal edilebildiği için, kullanıcı
     * gerçekten vazgeçmediyse alternatif Google kimlik akışı ile bir kez daha denenir.
     */
    suspend fun signIn(activityContext: Context): SignInResult {
        val credentialManager = CredentialManager.create(activityContext)
        val webClientId = activityContext.getString(R.string.default_web_client_id)

        val buttonRequest = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        val firstError: Exception = try {
            val credential = credentialManager.getCredential(activityContext, buttonRequest).credential
            return finishWithFirebase(activityContext, credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        Log.w(TAG, "Google düğme akışı başarısız: ${firstError.javaClass.simpleName}: ${firstError.message}")

        val firstKind = kindOf(firstError)
        if (firstKind == AuthErrorClassifier.Kind.USER_CANCELLED) return SignInResult.Cancelled
        if (firstError is NoCredentialException) return SignInResult.Failed(describe(activityContext, firstError))

        // Alternatif akış: Google kimlik alt sayfası (tüm hesaplar, otomatik seçim kapalı).
        val fallbackRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()

        val secondError: Exception = try {
            val credential = credentialManager.getCredential(activityContext, fallbackRequest).credential
            return finishWithFirebase(activityContext, credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        Log.e(TAG, "Alternatif Google akışı da başarısız", secondError)

        val secondKind = kindOf(secondError)
        val primary = when {
            // Kullanıcı ikinci pencereyi kapattıysa ilk (asıl) hatayı göster.
            secondKind == AuthErrorClassifier.Kind.USER_CANCELLED -> firstError
            secondKind == AuthErrorClassifier.Kind.CONFIGURATION -> secondError
            firstKind == AuthErrorClassifier.Kind.CONFIGURATION -> firstError
            else -> secondError
        }
        return SignInResult.Failed(describe(activityContext, primary, listOf(firstError, secondError)))
    }

    private fun kindOf(e: Exception): AuthErrorClassifier.Kind =
        AuthErrorClassifier.classify(e.message, isCancellation = e is GetCredentialCancellationException)

    private suspend fun finishWithFirebase(context: Context, credential: Credential): SignInResult {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return SignInResult.Failed(AuthFailure("Beklenmeyen hesap türü. Lütfen tekrar deneyin."))
        }
        return try {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val result = Firebase.auth.signInWithCredential(firebaseCredential).await()
            if (result.user != null) SignInResult.Success
            else SignInResult.Failed(AuthFailure("Oturum açılamadı. Lütfen tekrar deneyin."))
        } catch (e: Exception) {
            Log.e(TAG, "Firebase oturumu açılamadı", e)
            SignInResult.Failed(describe(context, e))
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

    private fun describe(context: Context, e: Exception, attempts: List<Exception> = listOf(e)): AuthFailure {
        val raw = e.message.orEmpty()
        val isDeveloperError = attempts.any { kindOf(it) == AuthErrorClassifier.Kind.CONFIGURATION }

        return when {
            isDeveloperError -> {
                val sha1 = AppSignature.fingerprint(context, "SHA-1") ?: "bilinmiyor"
                AuthFailure(
                    message = "Google ile giriş bu uygulama sürümü için henüz etkinleştirilmemiş. " +
                        "Lütfen kısa bir süre sonra tekrar deneyin veya uygulamayı güncelleyin.",
                    technicalDetails = "Google yapılandırma hatası (DEVELOPER_ERROR / reauth)\n" +
                        "Sürüm: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Paket: ${context.packageName}\n" +
                        "İmza SHA-1: $sha1\n" +
                        attempts.joinToString("") { "Hata: ${it.javaClass.simpleName}: ${it.message.orEmpty()}\n" } +
                        "Çözüm: Bu SHA-1 değeri Firebase Console › Proje ayarları › Android uygulaması bölümüne " +
                        "eklenmeli; OAuth izin ekranı 'Test' modundaysa hesap test kullanıcısı olarak eklenmeli " +
                        "veya uygulama yayınlanmalıdır."
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
