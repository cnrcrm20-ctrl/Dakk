package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dakk uçtan uca şifreleme motoru.
 *
 * - Her cihaz bir EC P-256 anahtar çifti üretir. Açık anahtar Firestore profilinde yayınlanır.
 * - Özel anahtar, Android Keystore'da tutulan bir AES-256-GCM anahtarıyla sarılarak saklanır.
 * - İki kişi arasındaki mesaj anahtarı: SHA-256(ECDH(benimÖzel, karşıAçık)).
 * - Mesajlar AES-256-GCM ile şifrelenir: Base64([12 bayt IV][şifreli veri + 16 bayt etiket]).
 */
class CryptoEngine(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("dakk_crypto_store", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private val sharedKeyCache = ConcurrentHashMap<String, SecretKeySpec>()

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    companion object {
        private const val TAG = "DakkCrypto"
        private const val PREF_PUB_KEY = "pref_ec_pub_key"
        private const val PREF_PRIV_KEY = "pref_ec_priv_key"
        private const val PREF_PRIV_KEY_WRAPPED = "pref_ec_priv_key_wrapped"
        private const val KEYSTORE_ALIAS = "dakk_identity_wrap_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }

    // --------------------------------------------------------------------------------------
    // Kimlik anahtarı
    // --------------------------------------------------------------------------------------

    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        val loaded = loadKeyPair()
        if (loaded != null) {
            cachedKeyPair = loaded
            return loaded
        }
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val pair = kpg.generateKeyPair()
        storeKeyPair(pair)
        cachedKeyPair = pair
        return pair
    }

    fun publicKeyBase64(): String =
        Base64.encodeToString(getOrCreateKeyPair().public.encoded, Base64.NO_WRAP)

    private fun loadKeyPair(): KeyPair? {
        val pubB64 = prefs.getString(PREF_PUB_KEY, null) ?: return null
        return try {
            val privBytes: ByteArray = prefs.getString(PREF_PRIV_KEY_WRAPPED, null)?.let { unwrap(it) }
                ?: prefs.getString(PREF_PRIV_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
                ?: return null
            val kf = KeyFactory.getInstance("EC")
            val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.NO_WRAP)))
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
            val pair = KeyPair(pub, priv)
            // Eski sürümlerden kalan düz metin anahtarı Keystore ile sarılmış hale taşı.
            if (prefs.contains(PREF_PRIV_KEY) && !prefs.contains(PREF_PRIV_KEY_WRAPPED)) {
                storeKeyPair(pair)
            }
            pair
        } catch (e: Exception) {
            Log.w(TAG, "Kayıtlı anahtar okunamadı, yeni anahtar üretilecek", e)
            null
        }
    }

    private fun storeKeyPair(pair: KeyPair) {
        val editor = prefs.edit()
            .putString(PREF_PUB_KEY, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
        val wrapped = try {
            wrap(pair.private.encoded)
        } catch (e: Exception) {
            Log.w(TAG, "Android Keystore kullanılamadı, anahtar uygulama özel alanında saklanıyor", e)
            null
        }
        if (wrapped != null) {
            editor.putString(PREF_PRIV_KEY_WRAPPED, wrapped).remove(PREF_PRIV_KEY)
        } else {
            editor.putString(PREF_PRIV_KEY, Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
        }
        editor.apply()
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun wrap(data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val out = cipher.iv + cipher.doFinal(data)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun unwrap(encoded: String): ByteArray {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, GCM_IV_BYTES)
        val body = all.copyOfRange(GCM_IV_BYTES, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    // --------------------------------------------------------------------------------------
    // Mesaj şifreleme
    // --------------------------------------------------------------------------------------

    private fun sharedKey(peerPublicKeyBase64: String): SecretKeySpec =
        sharedKeyCache.getOrPut(peerPublicKeyBase64) {
            val kf = KeyFactory.getInstance("EC")
            val peerKey = kf.generatePublic(
                X509EncodedKeySpec(Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP))
            )
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(getOrCreateKeyPair().private)
            agreement.doPhase(peerKey, true)
            val secret = MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
            SecretKeySpec(secret, "AES")
        }

    fun encrypt(plainText: String, peerPublicKeyBase64: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey(peerPublicKeyBase64), GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    fun decrypt(cipherTextBase64: String, peerPublicKeyBase64: String): String {
        val all = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        require(all.size > GCM_IV_BYTES + 16) { "Geçersiz şifreli veri" }
        val iv = all.copyOfRange(0, GCM_IV_BYTES)
        val body = all.copyOfRange(GCM_IV_BYTES, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, sharedKey(peerPublicKeyBase64), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(body), StandardCharsets.UTF_8)
    }

    /**
     * Bir mesajı, gönderici ve alıcı açık anahtarlarına bakarak çözer.
     * Bu cihazın anahtarı mesajın taraflarından biri değilse (ör. uygulama yeniden kurulduysa) null döner.
     */
    fun decryptMessage(cipherText: String, senderKey: String, recipientKey: String): String? {
        if (cipherText.isBlank() || senderKey.isBlank() || recipientKey.isBlank()) return null
        val myKey = publicKeyBase64()
        val otherKey = when (myKey) {
            senderKey -> recipientKey
            recipientKey -> senderKey
            else -> return null
        }
        return try {
            decrypt(cipherText, otherKey)
        } catch (e: Exception) {
            Log.w(TAG, "Mesaj çözülemedi", e)
            null
        }
    }

    /**
     * İki kişinin açık anahtarlarından, her iki cihazda da aynı görünen 60 haneli güvenlik numarası üretir.
     */
    fun safetyNumber(peerPublicKeyBase64: String): String {
        val keys = listOf(publicKeyBase64(), peerPublicKeyBase64).sorted()
        val digest = MessageDigest.getInstance("SHA-512")
            .digest(keys.joinToString("|").toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (i in 0 until 12) {
            val chunk = ((digest[i * 3].toInt() and 0xFF) shl 16) or
                ((digest[i * 3 + 1].toInt() and 0xFF) shl 8) or
                (digest[i * 3 + 2].toInt() and 0xFF)
            sb.append((chunk % 100000).toString().padStart(5, '0'))
            if (i != 11) sb.append(if (i % 4 == 3) "\n" else " ")
        }
        return sb.toString()
    }
}
