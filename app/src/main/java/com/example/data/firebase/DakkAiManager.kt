package com.example.data.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "DakkAiManager"

class DakkAiManager {

    // gemini-3.5-flash-lite is high-speed and ultra cost-effective for high-volume use cases (500k users)
    private val flashLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
            }
        )
    }

    private val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
            }
        )
    }

    // Local response cache to reduce duplicate queries from 500,000 active users
    private val responseCache = mutableMapOf<String, String>()

    suspend fun askDakkAi(prompt: String, systemContext: String = "Sen Dakk Messenger'ın zeki ve sevimli ördek asistanısın 🐥"): String {
        val cacheKey = prompt.trim().lowercase()
        if (responseCache.containsKey(cacheKey)) {
            return responseCache[cacheKey]!!
        }

        val fullPrompt = "$systemContext\n\nKullanıcı Mesajı: $prompt"

        return try {
            val response = withTimeout(30_000L) {
                try {
                    flashLiteModel.generateContent(fullPrompt)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Flash-lite failed, falling back to 3.8-flash: ${e.message}")
                    flashModel.generateContent(fullPrompt)
                }
            }
            val text = response.text ?: "Vak vak! Yanıt üretilemedi 🐥"
            if (responseCache.size > 200) {
                responseCache.clear()
            }
            responseCache[cacheKey] = text
            text
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            "Vak! Yanıt alırken zaman aşımı oluştu. Lütfen tekrar deneyin 🐥"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic request failed", e)
            "Vak vak! Şimdilik AI servisimize ulaşılamıyor: ${e.localizedMessage ?: "Bilinmeyen hata"}"
        }
    }
}
