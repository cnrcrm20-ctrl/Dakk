package com.example.assistant

import android.content.Context
import android.util.Log
import com.example.config.PrivateConfig
import com.example.config.PublicConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Asistan sohbetindeki bir satır. role: "user" veya "model". */
data class AssistantTurn(val role: String, val text: String, val time: Long)

class AssistantException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Dakk Asistan'ın yapay zekâ bağlantısı.
 *
 * Varsayılan: Firebase AI Logic üzerinden Gemini (ücretsiz katman, uygulamada API anahtarı yok).
 * Kurucu panelinden "özel API" seçilirse OpenAI uyumlu bir uç nokta kullanılır; anahtar uygulamaya
 * hiç gelmez, istek Dakk sunucusu üzerinden geçer. Kurucular kendi cihazlarında
 * private ayarları okuyabildikleri için uç noktayı doğrudan çağırabilir.
 */
class AssistantEngine(context: Context) {

    private val appContext = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private var cachedModel: GenerativeModel? = null
    private var cachedModelKey: String? = null

    suspend fun reply(
        config: PublicConfig,
        privateConfig: PrivateConfig?,
        history: List<AssistantTurn>,
        prompt: String,
        extraInstruction: String? = null
    ): String {
        val system = buildString {
            append(config.assistantSystemPrompt.ifBlank { DEFAULT_PROMPT })
            append("\n\nBugünün tarihi: ")
            append(SimpleDateFormat("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR")).format(Date()))
            if (!extraInstruction.isNullOrBlank()) append("\n\n").append(extraInstruction)
        }
        val turns = sanitize(history.takeLast(MAX_HISTORY_TURNS))
        val text = try {
            when {
                config.assistantProvider == PublicConfig.PROVIDER_CUSTOM && privateConfig?.isUsable == true ->
                    callOpenAiCompatible(privateConfig, system, turns, prompt)
                config.assistantProvider == PublicConfig.PROVIDER_CUSTOM && config.hasServer ->
                    callServer(config.serverUrl, system, turns, prompt)
                else -> callGemini(config.assistantModel, system, turns, prompt)
            }
        } catch (e: AssistantException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Asistan yanıt veremedi", e)
            throw AssistantException(friendlyError(e), e)
        }
        return text.trim().ifBlank { throw AssistantException("Asistan boş bir yanıt verdi. Tekrar deneyin.") }
    }

    // ------------------------------------------------------------------ Gemini (Firebase AI Logic)

    private suspend fun callGemini(modelName: String, system: String, turns: List<AssistantTurn>, prompt: String): String {
        val key = "$modelName|$system"
        val model = cachedModel?.takeIf { cachedModelKey == key } ?: Firebase
            .ai(backend = GenerativeBackend.googleAI())
            .generativeModel(modelName = modelName, systemInstruction = content { text(system) })
            .also {
                cachedModel = it
                cachedModelKey = key
            }
        val chat = model.startChat(history = turns.map { t -> content(role = t.role) { text(t.text) } })
        return chat.sendMessage(prompt).text.orEmpty()
    }

    // ------------------------------------------------------------------ OpenAI uyumlu uç nokta

    private fun openAiMessages(system: String, turns: List<AssistantTurn>, prompt: String): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        turns.forEach { t ->
            messages.put(JSONObject().put("role", if (t.role == "model") "assistant" else "user").put("content", t.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        return messages
    }

    private suspend fun callOpenAiCompatible(
        config: PrivateConfig,
        system: String,
        turns: List<AssistantTurn>,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", config.customApiModel)
            .put("messages", openAiMessages(system, turns, prompt))
            .toString()
        val request = Request.Builder()
            .url("${config.customApiBaseUrl}/chat/completions")
            .header("Authorization", "Bearer ${config.customApiKey}")
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw AssistantException("Özel API hata verdi (HTTP ${response.code}).")
            JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        }
    }

    private suspend fun callServer(serverUrl: String, system: String, turns: List<AssistantTurn>, prompt: String): String {
        val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw AssistantException("Oturum doğrulanamadı. Tekrar giriş yapın.")
        return withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("system", system)
                .put("messages", openAiMessages(system, turns, prompt).let { all ->
                    JSONArray().apply { for (i in 1 until all.length()) put(all.get(i)) }
                })
                .toString()
            val request = Request.Builder()
                .url("$serverUrl/assistant")
                .header("Authorization", "Bearer $token")
                .post(body.toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                    throw AssistantException(msg?.ifBlank { null } ?: "Asistan sunucusu yanıt vermedi (HTTP ${response.code}).")
                }
                JSONObject(raw).optString("text")
            }
        }
    }

    // ------------------------------------------------------------------ Yardımcılar

    /** Geçmiş "user" ile başlamalı ve roller sırayla değişmeli; ardışık aynı roller birleştirilir. */
    private fun sanitize(history: List<AssistantTurn>): List<AssistantTurn> {
        val out = ArrayList<AssistantTurn>()
        history.filter { it.text.isNotBlank() }.forEach { t ->
            val last = out.lastOrNull()
            if (last != null && last.role == t.role) {
                out[out.size - 1] = last.copy(text = last.text + "\n" + t.text)
            } else out.add(t)
        }
        while (out.firstOrNull()?.role == "model") out.removeAt(0)
        if (out.lastOrNull()?.role == "user") out.removeAt(out.size - 1)
        return out
    }

    private fun friendlyError(e: Exception): String {
        val text = (e.message.orEmpty() + " " + e.javaClass.simpleName).lowercase()
        return when {
            "quota" in text || "429" in text || "exhausted" in text ->
                "Asistan şu an çok yoğun. Biraz sonra tekrar deneyin."
            "api has not been used" in text || "not enabled" in text || "permission" in text || "403" in text ->
                "Asistan henüz etkinleştirilmemiş. Lütfen daha sonra tekrar deneyin."
            "unable to resolve host" in text || "timeout" in text || "network" in text || "unknownhost" in text ->
                "İnternet bağlantınızı kontrol edin."
            "safety" in text || "blocked" in text ->
                "Bu isteğe yanıt verilemedi. Farklı şekilde sormayı deneyin."
            else -> "Asistan şu an yanıt veremiyor. Tekrar deneyin."
        }
    }

    // ------------------------------------------------------------------ Yerel geçmiş

    private val historyFile get() = File(appContext.filesDir, "assistant_history.json")

    fun loadHistory(): List<AssistantTurn> = try {
        if (!historyFile.isFile) emptyList() else {
            val arr = JSONArray(historyFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    AssistantTurn(o.optString("r", "user"), o.optString("x"), o.optLong("t"))
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveHistory(turns: List<AssistantTurn>) {
        val arr = JSONArray()
        turns.takeLast(MAX_STORED_TURNS).forEach { t ->
            arr.put(JSONObject().put("r", t.role).put("x", t.text).put("t", t.time))
        }
        runCatching { historyFile.writeText(arr.toString()) }
    }

    fun clearHistory() {
        historyFile.delete()
    }

    companion object {
        private const val TAG = "DakkAssistant"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val MAX_HISTORY_TURNS = 30
        const val MAX_STORED_TURNS = 200

        const val DEFAULT_PROMPT =
            "Sen Dakk Asistan'sın: Dakk mesajlaşma uygulamasının içindeki yardımsever yapay zekâ asistanı. " +
                "Kullanıcıyla Türkçe konuş; kullanıcı başka bir dilde yazarsa o dilde cevap ver. " +
                "Samimi, kısa ve net ol; gereksiz uzatma. Emin olmadığın bilgiyi uydurma, bilmediğini söyle. " +
                "Sağlık, hukuk ve para konularında genel bilgi ver ve gerektiğinde bir uzmana danışmayı öner. " +
                "Markdown başlıkları kullanma; gerekirse kısa maddeler kullan."
    }
}
