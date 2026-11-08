# Dakk — Güvenli, Bağımsız ve Sıfır Maliyetli Doğrudan P2P Mesajlaşma

<p align="center">
  <img src="app/src/main/res/drawable/dakk_logo.png" width="160" alt="Dakk Maskot Logo" />
</p>

<p align="center">
  <strong>Merkezi Sunucusuz • Sıfır Maliyetli (0 TL) • Uçtan Uca Şifreli • Gerçek Doğrudan WebRTC P2P</strong>
</p>

---

## 🚀 Dakk Nedir?
**Dakk**, kullanıcıların telefon numarası, e-posta adresi, gerçek isim veya rehber senkronizasyonu paylaşmadan; tamamen yerel kriptografik kimliklerle doğrudan cihazdan cihaza (Peer-to-Peer) mesajlaşmasını sağlayan modern bir Android uygulamasıdır.

### 🌟 Temel İlkeler ve Özellikler
1. **Sıfır Sunucu Maliyeti (0 TL)**: Merkezi mesajlaşma sunucusu, röle sunucusu veya veritabanı gerektirmez.
2. **Gerçek WebRTC DataChannel**: Mesajlar cihazlar arasında doğrudan UDP/DTLS üzerinden iletilir.
3. **Kesinlikle TURN ve Bluetooth Yoktur**: Yalnızca açık ve ücretsiz STUN sunucuları (`stun.l.google.com:19302`) üzerinden doğrudan internet bağlantısı denenir.
4. **Çift Katmanlı Kriptografi**: WebRTC'nin kendi DTLS şifrelemesine ek olarak uygulama katmanında **Curve25519 (ECDH) + AES-256-GCM** uçtan uca şifreleme ve **ECDSA dijital imza** uygulanır.
5. **512 Karakterlik Güvenli Kimlik**: `SecureRandom` ile üretilen tam 512 karakterlik alfasayısal kullanıcı ID'si.
6. **Esnek Sinyalleşme Yöntemleri**:
   - **QR Kod**: Teklif (Offer) ve Cevap (Answer) Zlib ile sıkıştırılmış tek bir QR kod üzerinden karşılıklı taranır.
   - **Davet Bağlantısı**: WhatsApp, Telegram veya SMS üzerinden `dakk://invite?data=...` bağlantısı paylaşılır.
   - **6-Haneli PIN (Bulut Rendezvous)**: Uzaktaki kullanıcılar ortak 6 haneli bir kod ile sıfır maliyetli geçici pub-sub üzerinden eşleşebilir.
7. **Türkiye Operatör Test Laboratuvarı**: Turkcell, Vodafone, Türk Telekom, BİMcell, Netgsm ve Wi-Fi ağları için canlı STUN gecikmesi, ICE durumları ve paket RTT telemetrisi.
8. **Yerel Veri Depolama**: Mesajlar, kişiler ve ağ testleri yalnızca cihazınızda SQLite/Room üzerinde saklanır; buluta asla aktarılmaz.

---

## 🛠️ Mimari ve Teknoloji Seçimleri
- **Dil**: %100 Modern Kotlin (Coroutines, StateFlow, Flow)
- **Arayüz**: Jetpack Compose & Material 3 (OLED Saf Siyah Koyu Tema, Dakk Sarı/Turuncu marka renkleri)
- **Ağ**: `io.github.webrtc-sdk:android` (Resmi Maven Central WebRTC motoru)
- **Yerel Veritabanı**: Android Room (`AppDatabase`, `ContactDao`, `MessageDao`, `NetworkLogDao`)
- **Kriptografi**: Java Cryptography Architecture (JCA) - `secp256r1`, `AES/GCM/NoPadding`, `SHA256withECDSA`
- **QR Kod**: ZXing Core (`com.google.zxing:core`)

---

## 📱 İki Cihaz Arasında Test Talimatı (Adım Adım)

### Yöntem A: QR Kod ile Eşleşme (Yüz Yüze)
1. **Cihaz A**: "P2P Eşleş" sekmesinden **"Yeni Teklif (Offer) Üret"** butonuna basar. Ekranda yüksek kontrastlı QR kod belirir.
2. **Cihaz B**: Cihaz A'nın ekranındaki kodu kamerasıyla tarar veya paylaşılan kodu yapıştırarak **"Cevap (Answer) Üret"** butonuna basar.
3. **Cihaz A**: Cihaz B'nin ürettiği Cevap kodunu okur ve **"Cevabı Uygula"** butonuna basar.
4. ICE bağlantısı anında kurulur (`ICE: CONNECTED`), DataChannel açılır ve şifreli mesajlaşma başlar!

### Yöntem B: 6 Haneli PIN ile Eşleşme (Uzakta İken)
1. İki arkadaş aralarında 6 haneli bir kod belirler (örneğin: `948210`).
2. **Cihaz A**: "6-Haneli PIN" sekmesinde `948210` yazıp **"Oda Kur (Teklif)"** butonuna basar.
3. **Cihaz B**: Aynı kodu yazıp **"Odaya Katıl"** butonuna basar.
4. Cihazlar internet üzerinden saniyeler içinde doğrudan P2P el sıkışmasını tamamlar.

---

## 🏗️ Derleme ve Kurulum (Build & Install)

### Gereksinimler
- Android SDK 24+ (Android 7.0 ve üzeri destekler, Android 15/16 ile tam uyumludur)
- Java 11 / 17
- Gradle 8+

### Komut Satırından Derleme:
```bash
# Debug APK Derleme:
gradle assembleDebug

# Üretilen APK Dosyası:
# app/build/outputs/apk/debug/app-debug.apk

# Testleri Çalıştırma:
gradle :app:testDebugUnitTest
```

---

## 🔒 Güvenlik ve Gizlilik Beyanı
- **IP Adresi Şeffaflığı**: Doğrudan P2P bağlantılarda NAT delme (STUN) sonucunda karşı taraf genel IP adresinizi görebilir. Dakk bunu gizlemez, dürüstçe bildirir.
- **Sıfır İzleme**: Hiçbir reklam kimliği, analitik veya kullanıcı takip kodu içermez.

---

## 📄 Lisans
Bu proje **AGPL-3.0** (GNU Affero General Public License v3.0) altında lisanslanmıştır.
