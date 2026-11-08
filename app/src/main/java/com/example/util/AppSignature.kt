package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.PackageInfo
import android.content.pm.Signature
import java.io.File
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

    /** İndirilmiş (kurulmamış) bir APK dosyasının imza parmak izi. */
    fun archiveFingerprint(context: Context, apk: File, algorithm: String = "SHA-256"): String? = try {
        val pm = context.packageManager
        val info: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        }
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info?.signingInfo
            when {
                signing == null -> null
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info?.signatures
        }
        signatures?.firstOrNull()?.toByteArray()?.let { cert ->
            MessageDigest.getInstance(algorithm).digest(cert)
                .joinToString(":") { "%02X".format(it) }
        }
    } catch (_: Exception) {
        null
    }
}
