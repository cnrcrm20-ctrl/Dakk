package com.example.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Gizli sohbetler için kilit: parmak izi / yüz, yoksa telefonun ekran kilidi (PIN, desen, şifre).
 *
 * Android 9+ sistemin BiometricPrompt penceresini kullanır. Daha eski sürümlerde sistemin "kimliğini doğrula"
 * ekranı açılır (çoğu telefonda orada da parmak izi kabul edilir). Telefonda hiç ekran kilidi yoksa gizli sohbet
 * kullanılamaz; kullanıcıya ekran kilidi eklemesi söylenir.
 */
class BiometricGate internal constructor(
    private val context: Context,
    private val launchLegacy: (title: String, onSuccess: () -> Unit) -> Boolean
) {
    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val result = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
            if (result == BiometricManager.BIOMETRIC_SUCCESS) return true
        }
        return context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    fun authenticate(title: String, onSuccess: () -> Unit) {
        if (!isAvailable()) {
            Toast.makeText(
                context,
                "Gizli sohbet için önce telefonuna parmak izi ya da ekran kilidi (PIN, desen) ekle.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val activity = context.findActivity() as? FragmentActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity != null) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Parmak izin veya ekran kilidinle doğrula")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            runCatching { prompt.authenticate(info) }.onFailure { launchLegacy(title, onSuccess) }
        } else if (!launchLegacy(title, onSuccess)) {
            Toast.makeText(context, "Kilit ekranı açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}

@Composable
fun rememberBiometricGate(): BiometricGate {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pending[0]
        pending[0] = null
        if (result.resultCode == Activity.RESULT_OK) callback?.invoke()
    }
    return remember(context) {
        BiometricGate(context) { title, onSuccess ->
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard?.createConfirmDeviceCredentialIntent(title, "Gizli sohbetleri açmak için doğrula")
            if (intent == null) false else {
                pending[0] = onSuccess
                runCatching { launcher.launch(intent) }.isSuccess
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
