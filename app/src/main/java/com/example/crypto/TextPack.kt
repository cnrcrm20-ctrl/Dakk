package com.example.crypto

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Mesaj metnini şifrelemeden önce sıkıştırır (2.5.0+).
 *
 * Biçim: [0x00][sözlük sürümü][ham deflate akışı][dolgu]. Deflate, sohbetlerde sık geçen Türkçe kelime ve
 * kalıplardan oluşan sabit bir sözlükle başlatılır; böylece kısa mesajlar da küçülür. Akışın sonu deflate
 * içinde işaretli olduğundan, uzunluğu gizlemek için eklenen dolgu baytları çözerken yok sayılır.
 * Sıkıştırılmış hal ham metinden küçük değilse metin olduğu gibi (UTF-8) gönderilir; çözücü ikisini de tanır.
 * Normal bir metin 0x00 baytıyla başlamadığı için eski mesajlarla karışmaz.
 */
object TextPack {

    private const val MARKER: Byte = 0
    private const val DICT_V1: Byte = 1
    /** Sıkıştırılmış veri bu baytın katlarına tamamlanır. */
    const val PAD_BLOCK = 16
    private const val MAX_INFLATED = 256 * 1024

    fun encode(text: String): ByteArray {
        val raw = text.toByteArray(StandardCharsets.UTF_8)
        if (raw.size < 12) return raw
        val packed = ByteArrayOutputStream(raw.size + PAD_BLOCK).apply {
            write(MARKER.toInt())
            write(DICT_V1.toInt())
            write(deflate(raw))
        }
        val padding = (PAD_BLOCK - packed.size() % PAD_BLOCK) % PAD_BLOCK
        repeat(padding) { packed.write(0) }
        val out = packed.toByteArray()
        return if (out.size < raw.size) out else raw
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == MARKER && bytes[1] == DICT_V1) {
            return String(inflate(bytes, 2), StandardCharsets.UTF_8)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun isPacked(bytes: ByteArray): Boolean = bytes.size >= 3 && bytes[0] == MARKER && bytes[1] == DICT_V1

    /** Sözlüksüz, en yüksek sıkıştırma (geçmiş paketleri gibi büyük veriler için). */
    fun deflateBytes(data: ByteArray): ByteArray = deflate(data, dictionary = null)

    fun inflateBytes(data: ByteArray, limit: Int = 8 * 1024 * 1024): ByteArray = inflate(data, 0, dictionary = null, limit = limit)

    private fun deflate(input: ByteArray, dictionary: ByteArray? = DICTIONARY): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            if (dictionary != null) deflater.setDictionary(dictionary)
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size / 2 + 16)
            val buf = ByteArray(4096)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(
        input: ByteArray,
        offset: Int,
        dictionary: ByteArray? = DICTIONARY,
        limit: Int = MAX_INFLATED
    ): ByteArray {
        val inflater = Inflater(true)
        try {
            if (dictionary != null) inflater.setDictionary(dictionary)
            inflater.setInput(input, offset, input.size - offset)
            val out = ByteArrayOutputStream(input.size * 3)
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
                require(out.size() <= limit) { "Çözülen veri çok büyük" }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /**
     * Sürüm 1 sözlüğü. DEĞİŞTİRİLMEMELİ: eski mesajlar bu baytlarla çözülür. Yeni bir sözlük gerekirse
     * yeni bir sürüm numarasıyla eklenmeli. Deflate sözlüğün sonuna yakın parçaları daha ucuza kodladığı için
     * en sık geçen ifadeler sondadır.
     */
    private val DICTIONARY: ByteArray = (
        "{\"v\":1,\"type\":\"image\",\"mediaId\":\"\",\"chunks\":1,\"st\":\"r\",\"mime\":\"image/jpeg\",\"w\":1280,\"h\":960,\"size\":" +
            "\"thumb\":\"/9j/4AAQSkZJRgABAQAAAQABAAD/\",\"tf\":\"webp\"} {\"q\":\"\",\"o\":[\"\",\"\"],\"m\":false,\"k\":\"match\"} " +
            "https://www. .com http:// @dakk 📷 Fotoğraf 📊 Anket ⚽ maç halı saha kadro takım gol kaleci " +
            "pazartesi salı çarşamba perşembe cuma cumartesi pazar ocak şubat mart nisan mayıs haziran temmuz ağustos " +
            "eylül ekim kasım aralık sabah öğlen akşam gece bugün yarın dün şimdi hemen sonra önce birazdan " +
            "saat dakika saniye hafta ay yıl kaçta kaç tane nerede nereye nereden ne zaman neden niye nasıl hangi kim " +
            "kimin bende sende onda bizde sizde onlarda benim senin onun bizim sizin onların bana sana ona bize size " +
            "beni seni onu bizi sizi onları evde okulda işte dışarıda yoldayım geliyorum gidiyorum çıkıyorum " +
            "geldim gittim çıktım vardım bekliyorum bekle bekliyorum seni arayacağım ararım aradım açmadın " +
            "telefon mesaj attım yazdım gördüm görmedim okudum duydum biliyorum bilmiyorum anladım anlamadım " +
            "istiyorum istemiyorum yapıyorum yapmıyorum yaptım yapmadım yapacağım olur olmaz oldu olmadı olacak " +
            "olsun var yok değil mi mı mu mü misin musun mısın müsün miyiz miydi değilim değilsin " +
            "teşekkür ederim teşekkürler sağ ol sağol eyvallah rica ederim kusura bakma özür dilerim pardon " +
            "günaydın iyi geceler iyi akşamlar iyi günler hayırlı olsun geçmiş olsun kolay gelsin afiyet olsun " +
            "doğum günün kutlu olsun iyi ki doğdun bayramın kutlu olsun tebrikler canım aşkım kardeşim abi abla " +
            "kanka kanki hocam annem babam evet hayır tamam tamamdır peki olur yani aslında galiba herhalde " +
            "kesinlikle gerçekten çok güzel çok iyi süper harika mükemmel berbat kötü fena değil idare eder " +
            "ne yapıyorsun napıyorsun naber nasılsın iyiyim sen nasılsın ne var ne yok iyidir senden " +
            "hadi gel gidelim buluşalım görüşürüz görüşmek üzere kendine iyi bak öptüm sevgiler selamlar " +
            "merhaba selam slm mrb tmm tşk tşkler hahaha haha ahahah aynen aynen öyle bence sence bilmem " +
            "bir şey bir şeyler her şey hiçbir şey birisi herkes kimse biraz çok daha en az fazla bazen hep hiç " +
            "ama fakat ancak çünkü ki de da ile ve veya ya da ise eğer için gibi kadar göre beri doğru " +
            "bu şu o bunlar şunlar bunu şunu bunun şunun burada şurada orada buraya oraya buradan oradan " +
            "geç kaldım geç kalacağım erken geldim yemek yedin mi yedim su kahve çay para lira tl ödedim öderim " +
            "ödedin mi hesap iban gönderdim gönderir misin yolla at bakar mısın bakıyorum baktım bak " +
            "ders sınav ödev toplantı iş çalışıyorum müsait misin müsaitim meşgulüm uygun musun uygunum " +
            "acil önemli lütfen bir dakika bi dk bi sn geliyorum hemen geliyorum kapıdayım aşağıdayım " +
            "ne diyorsun ne dedin dediğim gibi söyledim söyle anlat konuşalım konuşuruz sonra konuşuruz " +
            "seni seviyorum çok özledim özledim seni de canım benim hayatım tatlım güzelim yakışıklı " +
            "mı acaba ne oldu neler oluyor bir şey olmadı sorun yok sorun değil merak etme üzülme boş ver " +
            "ın in un ün yor yorum yorsun yoruz yorlar acak ecek acağım eceğim mış miş muş müş dı di du dü tı ti tu tü " +
            "lar ler ların lerin ları leri lara lere da de ta te dan den tan ten la le yla yle ıyor iyor uyor üyor " +
            "çok teşekkür ederim tamam görüşürüz ne yapıyorsun nasılsın iyiyim sen tamam olur evet hayır " +
            "geliyorum bekliyorum şimdi değil mi bir de ama ben de sen de çok güzel olmuş teşekkürler canım tamamdır"
        ).toByteArray(StandardCharsets.UTF_8)
}
