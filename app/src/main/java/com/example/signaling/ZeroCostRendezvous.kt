package com.example.signaling

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sıfır maliyetli (0 TL) Açık Oda ve PIN Bazlı Eşleşme Sinyalleşmesi.
 * Kullanıcılar aynı anda uzakta olduklarında 6 haneli bir PIN kodu (örn. 839210) belirleyerek
 * QR kod taramaya gerek kalmadan teklif (Offer) ve cevap (Answer) paketlerini
 * açık ve şifreli geçici rendezvous kanalı üzerinden anında takas edebilirler.
 */
class ZeroCostRendezvous(private val okHttpClient: OkHttpClient = defaultClient()) {

    companion object {
        private const val TAG = "DakkRendezvous"
        private const val BASE_URL = "https://ntfy.sh" // Tamamen ücretsiz, kayıtsız ve açık pub-sub servisi

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeCall: Call? = null
    private var isListening = false

    /**
     * Belirtilen PIN kodlu odaya şifrelenmiş kompakt sinyalleşme paketini yayınlar (Publish)
     */
    fun publishOfferOrAnswer(pinCode: String, payloadString: String, onComplete: (Boolean) -> Unit) {
        val topic = "dakk_p2p_room_$pinCode"
        val mediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, payloadString)
        val request = Request.Builder()
            .url("$BASE_URL/$topic")
            .post(body)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Publish failure: ${e.message}")
                mainHandler.post { onComplete(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                response.close()
                mainHandler.post { onComplete(success) }
            }
        })
    }

    /**
     * Belirtilen PIN kodlu odayı dinlemeye başlar (Subscribe / Long-Poll)
     */
    fun subscribeToRoom(pinCode: String, onPayloadReceived: (String) -> Unit) {
        stopListening()
        isListening = true
        val topic = "dakk_p2p_room_$pinCode"

        val request = Request.Builder()
            .url("$BASE_URL/$topic/raw")
            .build()

        val call = okHttpClient.newCall(request)
        activeCall = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isListening) {
                    Log.w(TAG, "Subscribe error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val content = response.body?.string()?.trim()
                    if (!content.isNullOrEmpty() && isListening) {
                        mainHandler.post {
                            onPayloadReceived(content)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Response read error: ${e.message}")
                } finally {
                    response.close()
                }
            }
        })
    }

    fun stopListening() {
        isListening = false
        activeCall?.cancel()
        activeCall = null
    }
}
