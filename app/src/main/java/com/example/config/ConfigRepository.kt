package com.example.config

import android.content.Context
import com.example.R
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Uygulama ayarları ve Kurucu paneli için Firestore erişimi.
 *
 * Kimin kurucu olduğu uygulamada YAZMAZ: firestore.rules içindeki isFounder() kararı verir.
 * Uygulama yalnızca app_config/private belgesini okumayı dener; izin varsa kullanıcı kurucudur.
 */
class ConfigRepository(context: Context) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    )
    private val publicDoc get() = db.collection(COLLECTION).document(DOC_PUBLIC)
    private val privateDoc get() = db.collection(COLLECTION).document(DOC_PRIVATE)
    private val panelDoc get() = db.collection(COLLECTION).document(DOC_PANEL)

    fun observePublic(): Flow<PublicConfig> =
        publicDoc.snapshots().map { PublicConfig.fromMap(it.data) }

    /** Kurucuysa private ayarları, değilse null döner. */
    suspend fun loadPrivateIfFounder(): PrivateConfig? = try {
        PrivateConfig.fromMap(privateDoc.get().await().data)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
    }

    suspend fun loadRaw(doc: String): Map<String, Any?> =
        db.collection(COLLECTION).document(doc).get().await().data ?: emptyMap()

    suspend fun saveValues(doc: String, values: Map<String, Any?>) {
        if (values.isEmpty()) return
        db.collection(COLLECTION).document(doc).set(values, SetOptions.merge()).await()
    }

    /** Panel şeması (JSON metni). Belge yoksa null. */
    suspend fun loadPanelSchema(): String? =
        panelDoc.get().await().getString("schema")?.takeIf { it.isNotBlank() }

    suspend fun savePanelSchema(json: String) {
        panelDoc.set(
            mapOf("schema" to json, "updated_at" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()
    }

    /** Depodaki güncel panel şemasını GitHub'dan indirir. */
    suspend fun downloadPanelSchema(): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(PANEL_SCHEMA_URL).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Panel şeması indirilemedi (HTTP ${response.code})" }
            response.body?.string().orEmpty().also { check(it.trim().startsWith("{")) { "Geçersiz panel şeması" } }
        }
    }

    suspend fun countUsers(): Long =
        db.collection("users").count().get(AggregateSource.SERVER).await().count

    companion object {
        const val COLLECTION = "app_config"
        const val DOC_PUBLIC = "public"
        const val DOC_PRIVATE = "private"
        const val DOC_PANEL = "admin_panel"
        const val PANEL_SCHEMA_URL =
            "https://raw.githubusercontent.com/cnrcrm20-ctrl/Dakk/main/cloud/admin_panel.json"
    }
}
