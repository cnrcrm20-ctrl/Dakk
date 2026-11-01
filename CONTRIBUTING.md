# Katkıda Bulunma Kılavuzu (CONTRIBUTING)

Dakk projesine katkıda bulunmak istediğiniz için teşekkür ederiz! Dakk, merkeziyetsiz, açık kaynaklı ve gizlilik odaklı bir projedir.

## İlkelerimiz
1. **Sıfır Sunucu & 0 TL**: Hiçbir merkezi sunucu zorunluluğu veya ücretli servis bağımlılığı eklenemez.
2. **TURN ve Bluetooth Yasağı**: İletişim doğrudan WebRTC STUN tabanlı olmalıdır.
3. **Kullanıcı Verisi Koruması**: Asla telefon numarası, e-posta, konum veya rehber izni talep edilemez.
4. **Kod Kalitesi**: Modern Kotlin, Jetpack Compose ve Clean Architecture prensiplerine sadık kalınmalıdır.

## Pull Request Süreci
1. Depoyu fork'layın ve `feature/yeni-ozellik` dalı oluşturun.
2. Birim testleri yazın (`gradle :app:testDebugUnitTest`).
3. Kod formatını kontrol edin.
4. Açıklayıcı bir PR başlığı ile katkınızı gönderin.
