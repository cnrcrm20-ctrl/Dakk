package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Kurulu APK'nın imza sertifikası parmak izlerini döndürür (Firebase yapılandırma teşhisi için). */
object AppSignature {

    fun fingerprint(context: Context, algorithm: String = "SHA-1"): String? = try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo
            when {
                signing == null -> null
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.firstOrNull()?.toByteArray()?.let { cert ->
            MessageDigest.getInstance(algorithm).digest(cert)
                .joinToString(":") { "%02X".format(it) }
        }
    } catch (_: Exception) {
        null
    }
}
