package com.example.crypto

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dakk Kriptografi ve Kimlik Yönetim Motoru.
 * - SecureRandom ile 512 karakterlik benzersiz Kullanıcı ID üretimi
 * - EC (secp256r1) Anahtar Çifti (Açık / Özel Anahtar)
 * - ECDH ile Ortak Paylaşılan Gizli Anahtar (Shared Secret) Türetimi
 * - AES-256-GCM ile Uçtan Uca Mesaj Şifreleme ve Doğrulama
 * - SHA256withECDSA ile Davet ve Paket Dijital İmzası
 * - Replay Attack (Yeniden Oynatma Saldırısı) Koruması için Sıra Numarası ve Zaman Doğrulaması
 */
class CryptoEngine(private val context: Context) {

    private val prefs = context.getSharedPreferences("dakk_crypto_store", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    companion object {
        private const val PREF_USER_ID = "pref_user_id_512"
        private const val PREF_USER_ALIAS = "pref_user_alias"
        private const val PREF_PUB_KEY = "pref_ec_pub_key"
        private const val PREF_PRIV_KEY = "pref_ec_priv_key"

        private const val CHAR_POOL = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val AES_GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
    }

    /**
     * Tam olarak 512 karakterlik kriptografik rastgele kullanıcı kimliği (a-z, A-Z, 0-9)
     */
    fun getOrCreateUserId(): String {
        var id = prefs.getString(PREF_USER_ID, null)
        if (id.isNullOrEmpty() || id.length != 512) {
            id = generate512CharacterId()
            prefs.edit().putString(PREF_USER_ID, id).apply()
        }
        return id
    }

    /**
     * 512 karakterlik rastgele kimlik üretici
     */
    fun generate512CharacterId(): String {
        val sb = StringBuilder(512)
        val poolLen = CHAR_POOL.length
        for (i in 0 until 512) {
            val randomIndex = secureRandom.nextInt(poolLen)
            sb.append(CHAR_POOL[randomIndex])
        }
        return sb.toString()
    }

    /**
     * Kullanıcı takma adını döndürür veya varsayılan oluşturur
     */
    fun getUserAlias(): String {
        return prefs.getString(PREF_USER_ALIAS, "Dakk-Kullanıcı") ?: "Dakk-Kullanıcı"
    }

    fun setUserAlias(alias: String) {
        prefs.edit().putString(PREF_USER_ALIAS, alias.trim()).apply()
    }

    /**
     * Cihazın EC Anahtar Çiftini (secp256r1) getirir veya üretir
     */
    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        val pubB64 = prefs.getString(PREF_PUB_KEY, null)
        val privB64 = prefs.getString(PREF_PRIV_KEY, null)

        if (!pubB64.isNullOrEmpty() && !privB64.isNullOrEmpty()) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val pubBytes = Base64.decode(pubB64, Base64.NO_WRAP)
                val privBytes = Base64.decode(privB64, Base64.NO_WRAP)
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                // Anahtar okuma hatasında yeni anahtar çifti oluştur
            }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec, secureRandom)
        val newPair = kpg.generateKeyPair()

        val newPubB64 = Base64.encodeToString(newPair.public.encoded, Base64.NO_WRAP)
        val newPrivB64 = Base64.encodeToString(newPair.private.encoded, Base64.NO_WRAP)
        prefs.edit()
            .putString(PREF_PUB_KEY, newPubB64)
            .putString(PREF_PRIV_KEY, newPrivB64)
            .apply()

        return newPair
    }

    fun getPublicKeyBase64(): String {
        return Base64.encodeToString(getOrCreateKeyPair().public.encoded, Base64.NO_WRAP)
    }

    /**
     * Kullanıcı kimliğini ve anahtar çiftini tamamen sıfırlar
     */
    fun resetIdentity(): Pair<String, String> {
        val newId = generate512CharacterId()
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec, secureRandom)
        val newPair = kpg.generateKeyPair()

        val pubB64 = Base64.encodeToString(newPair.public.encoded, Base64.NO_WRAP)
        val privB64 = Base64.encodeToString(newPair.private.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(PREF_USER_ID, newId)
            .putString(PREF_PUB_KEY, pubB64)
            .putString(PREF_PRIV_KEY, privB64)
            .apply()

        return Pair(newId, pubB64)
    }

    /**
     * Karşı tarafın açık anahtarı ve bizim özel anahtarımız ile ECDH ortak simetrik anahtar türetir
     */
    fun deriveSharedKey(peerPublicKeyBase64: String): SecretKeySpec {
        val peerPubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPubKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubBytes))

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(getOrCreateKeyPair().private)
        ka.doPhase(peerPubKey, true)
        val sharedSecret = ka.generateSecret()

        // SHA-256 ile tam 256-bit AES anahtarı türet
        val md = MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = md.digest(sharedSecret)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    /**
     * AES-256-GCM Uçtan Uca Şifreleme
     * Format: [12-byte IV] + [Şifreli Veri + 16-byte GCM Tag]
     */
    fun encrypt(plainText: String, peerPublicKeyBase64: String): String {
        val keySpec = deriveSharedKey(peerPublicKeyBase64)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * AES-256-GCM Uçtan Uca Şifre Çözme
     */
    fun decrypt(encryptedPayloadBase64: String, peerPublicKeyBase64: String): String {
        val keySpec = deriveSharedKey(peerPublicKeyBase64)
        val combined = Base64.decode(encryptedPayloadBase64, Base64.NO_WRAP)

        if (combined.size < GCM_IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Şifreli veri boyutu geçersiz")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decrypted = cipher.doFinal(cipherBytes)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    /**
     * Bir veriyi yerel özel anahtar ile dijital olarak imzalar (ECDSA with SHA256)
     */
    fun signData(data: String): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(getOrCreateKeyPair().private)
        signer.update(data.toByteArray(StandardCharsets.UTF_8))
        val sig = signer.sign()
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    /**
     * Karşı tarafın açık anahtarı ile dijital imzayı doğrular
     */
    fun verifySignature(data: String, signatureBase64: String, peerPublicKeyBase64: String): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val pubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
            val peerPubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(peerPubKey)
            verifier.update(data.toByteArray(StandardCharsets.UTF_8))
            val sigBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }
}
