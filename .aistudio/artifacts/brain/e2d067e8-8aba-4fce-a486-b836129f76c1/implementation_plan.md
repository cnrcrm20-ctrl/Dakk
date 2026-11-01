# Dakk — Güvenli, Bağımsız ve Sıfır Maliyetli P2P Mesajlaşma Uygulaması

Dakk, merkezi hiçbir sunucuya, telefon numarasına veya kullanıcı kaydına ihtiyaç duymayan; doğrudan cihazdan cihaza (Peer-to-Peer) WebRTC DataChannel ve uygulama seviyesinde AES-256-GCM uçtan uca şifreleme ile çalışan modern bir Android mesajlaşma platformudur.

---

### Kullanıcı Tercihleri ve Kesinleşen Kararlar

> [!IMPORTANT]
> Kullanıcı ile yapılan ön görüşmede belirlenen mimari ve görsel tercihler şunlardır:

- **Sinyalleşme Yöntemi**: Sıfır maliyetli (0 TL) hibrit yöntem. Parçalı/sıkıştırılmış QR kodlar ve mesajlaşma uygulamaları (WhatsApp, Telegram vb.) üzerinden paylaşılan derin davet bağlantıları (Deep Link / Pano) birincil yöntem olarak kullanılacaktır. Ayrıca arka planda tek kullanımlık açık şifreli sinyal kanalları desteklenecektir.
- **Marka ve Logo**: Kullanıcının yüklediği orijinal Dakk ördek maskotlu sarı-turuncu-siyah-beyaz logo, uygulamanın resmi simgesi (App Icon), açılış ekranı (Splash Screen) ve arayüz marka unsurlarında doğrudan kullanılacaktır.
- **Arayüz Teması**: Modern Koyu Tema (OLED Saf Siyah, temiz beyaz tipografi ve logodaki sarı/turuncu canlı vurgu renkleri).
- **Ağ Kapsamı**: Türkiye operatörleri (Turkcell, Vodafone, Türk Telekom, BİMcell, Netgsm) ve Wi-Fi ağları için gerçek zamanlı STUN, ICE ve DataChannel telemetrisi.

---

## 1. Genel Bakış ve Temel Konsept

- **Nedir?**: İki Android cihaz arasında merkezi bir röle, veri tabanı veya üçüncü taraf mesaj sunucusu olmaksızın, tamamen yerel kriptografik kimliklerle doğrudan P2P veri kanalı kuran ve mesaj alışverişi sağlayan bağımsız bir mesajlaşma aracıdır.
- **Kullanıcı Kitlesi**: Gizliliğine önem veren, merkezi sistemlere güvenmeyen, telefon numarası veya e-posta paylaşmak istemeyen ve doğrudan cihazdan cihaza güvenli iletişim kurmak isteyen kullanıcılar.
- **Temel Değerler**:
  - Sıfır merkezi altyapı maliyeti (0 TL).
  - Telefon no, e-posta, rehber veya konum izni ASLA istenmez.
  - TURN ve Bluetooth kullanılmaz; yalnızca STUN üzerinden doğrudan internet P2P bağlantısı hedeflenir.
  - Açık ve dürüst bağlantı durumu (bağlantı kurulamazsa simüle edilmez, gerçek ICE ve STUN durumu gösterilir).

---

## 2. Kullanıcı Deneyimi ve Görsel Tasarım

### Ana Kullanıcı Akışları
1. **İlk Kurulum ve Kimlik Üretimi**:
   - Açılışta 512 karakterlik kriptografik kullanıcı ID'si ve Curve25519 / Ed25519 (ECDH & Dijital İmza) anahtar çifti oluşturulur.
   - Kullanıcı yalnızca dilediği bir takma ad (rumuz) belirler.
2. **Arkadaş Ekleme ve El Sıkışma (Sinyalleşme)**:
   - **QR Kod ile**: Tek bir ekranda Offer QR oluşturulur, karşı taraf tarar ve Answer QR üretir. İki cihaz saniyeler içinde doğrudan P2P eşleşir.
   - **Davet Bağlantısı ile**: Sıkıştırılmış Base64 SDP/ICE bağlantısı kopyalanıp WhatsApp, Telegram veya SMS ile gönderilir. Karşı taraf tıkladığında Dakk açılır ve cevap paketini geri iletir.
3. **P2P Sohbet Ekranı**:
   - Gerçek zamanlı ICE bağlantı durumu (Checking, Connected, Disconnected, Failed).
   - Türkiye operatör ve bağlantı türü etiketi (Turkcell, Vodafone, Wi-Fi vb.).
   - Gönderildi / Teslim Edildi (ACK) tikleri, sıra numaralı mesaj akışı, anlık P2P mesajlaşma.
   - Sohbeti temizleme, mesaj silme ve kişiyi engelleme.
4. **Bağlantı & Ağ Teşhis Ekranı (Operatör Test Laboratuvarı)**:
   - STUN yanıt süresi, ICE candidate tipleri (host, srflx), paket gecikmesi (RTT), DataChannel açık/kapalı durumu.
5. **Güncelleme ve Profil**:
   - 512 karakterlik ID görüntüleme, QR oluşturma, kimlik yenileme.
   - GitHub Releases API üzerinden yeni sürüm denetimi ve güvenli APK indirme yönlendirmesi.

### Tasarım Sistemi ve Renk Paleti
- **Aesthetic Direction**: Siber-minimalist, yüksek kontrastlı OLED dark messenger.
- **Renk Paleti**:
  - `Background`: `#0A0A0C` (Derin OLED Siyah)
  - `Surface`: `#141418` (Koyu Gri Kartlar)
  - `Primary / Dakk Yellow`: `#FFCC00` (Canlı Dakk Sarısı)
  - `Secondary / Dakk Orange`: `#FF6B00` (Enerjik Turuncu)
  - `Text Primary`: `#FFFFFF`
  - `Text Secondary`: `#9E9EA7`
  - `Success`: `#00E676` (P2P Bağlandı)
  - `Error / Failed`: `#FF3B30` (ICE Başarısız)
- **Tipografi**: Net, modern sans-serif hiyerarşi, monospaced kriptografik anahtar görünümü.

---

## 3. Ürün Kararları ve Mimari Tercihler

| Karar | Seçilen Yaklaşım | Gerekçe & Avantaj | Alternatifler |
|---|---|---|---|
| **P2P İletişim** | WebRTC DataChannel (SCTP over DTLS) | Tamamen doğrudan cihazlar arası paket iletimi, merkezi sunucu yok | WebSocket sunucusu (merkezi sunucu ve maliyet gerektirir, reddedildi) |
| **STUN Sunucuları** | Google & Açık Standart STUN Havuzu (`stun.l.google.com:19302`) | Güvenilir, küresel, 0 TL maliyet, NAT arkasındaki genel IP/portu çözer | TURN (maliyetli ve veri rölesi yaptığı için kullanıcı isteğiyle kesinlikle yasaklandı) |
| **Sinyalleşme (Signaling)** | Zlib/Deflate sıkıştırmalı QR kodlar + Deep Link + Açık MQTT/WS ephemeral rendezvous | 0 TL sunucu masrafı, internete ihtiyaç duymadan yüz yüze eşleşme veya mesajlaşma linkiyle takas imkanı | Ücretli sinyalleşme sunucusu (kullanıcının 0 TL hedefiyle uyuşmaz) |
| **Uygulama İçi Şifreleme** | ECDH (Curve25519) + AES-256-GCM + HMAC SHA-256 + Sequence ACK | WebRTC'nin DTLS şifrelemesine ek olarak uçtan uca çift katmanlı kriptografik güvenlik ve replay koruması | Düz metin iletim (güvensiz) |
| **Yerel Veri Saklama** | Android Room Database + EncryptedSharedPreferences | Cihaz içinde %100 yerel, çevrimdışı çalışabilir, bulut bağımsızlığı | Firebase Firestore / Cloud DB (kullanıcı yerel saklama istediği için reddedildi) |

---

## 4. Teknik Mimari ve Veri Stratejisi

### Sistem Mimarisi Şeması

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DAKK ANDROID CLIENT                             │
├────────────────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose, Material 3, Dark Theme)                    │
│  ├── SplashScreen (Orijinal Dakk Maskot Logo)                          │
│  ├── ChatScreen & Active P2P Session                                   │
│  ├── HandshakeScreen (Parçalı QR Scanner / Deep Link Paylaşımı)        │
│  ├── NetworkDiagnosticScreen (STUN / ICE / Operatör Testleri)          │
│  └── Profile & Privacy Settings Screen                                 │
├────────────────────────────────────────────────────────────────────────┤
│  ViewModel & State Management (StateFlow, Coroutines)                  │
├────────────────────────────────────────────────────────────────────────┤
│  Core Domain & Services:                                               │
│  ├── WebRtcEngine (STUN Pool, PeerConnection, DataChannel Lifecycle)   │
│  ├── SignalingManager (SDP/ICE Compact Serializer, QR Chunker, Link)   │
│  ├── CryptoEngine (512-char ID, ECDH, AES-256-GCM, Digital Signatures) │
│  └── TelemetryManager (Ağ türü, Operatör seçimi, ICE Durumu, RTT)     │
├────────────────────────────────────────────────────────────────────────┤
│  Local Persistence (Room Database & Android Keystore):                 │
│  ├── ContactDao (Kullanıcı ID'leri, Açık Anahtarlar, Durumlar)        │
│  ├── MessageDao (Sohbet Geçmişi, Zaman Damgaları, ACK Durumu)          │
│  └── BlockedUserDao (Engellenen 512-karakterlik ID'ler)                │
└──────────────────┬───────────────────────────────────┬─────────────────┘
                   │ Direct P2P via STUN               │ Sinyalleşme
                   ▼                                   ▼
        ┌─────────────────────┐              ┌──────────────────┐
        │  Karşı Dakk Cihazı  │              │ QR / Deep Link / │
        │  (Direct UDP/DTLS)  │              │ Ephemeral broker │
        └─────────────────────┘              └──────────────────┘
```

### Veri Modelleri (Room Varlıkları)
1. **ContactEntity**: `userId` (512-char), `alias`, `publicKey`, `addedTimestamp`, `isBlocked`, `lastSeen`
2. **MessageEntity**: `id`, `peerId`, `content`, `timestamp`, `isFromMe`, `status` (PENDING, SENT, DELIVERED, FAILED), `sequenceNumber`
3. **NetworkSessionLog**: `id`, `peerId`, `operatorName`, `networkType`, `iceState`, `stunLatencyMs`, `connectionDurationMs`

### Sinyalleşme ve Bağlantı Adımları
1. **Teklif (Offer) Üretimi**:
   - Cihaz A yeni bir `PeerConnection` ve `DataChannel` başlatır.
   - STUN sunucularından ICE candidate'ları toplar.
   - SDP ve adaylar JSON formatında toplanıp gzip/deflate ile sıkıştırılır, Base64 formatına çevrilir.
   - Ekran üzerinde animasyonlu/parçalı QR veya "Davet Bağlantısı Kopyala/Paylaş" butonu sunulur.
2. **Cevap (Answer) Üretimi**:
   - Cihaz B QR kodu kamerasıyla tarar veya paylaşılan bağlantıyı tıklar.
   - Teklifi içe aktarır, kendi yerel SDP cevabını ve adaylarını üretir.
   - Answer QR veya Answer Link oluşturup Cihaz A'ya gösterir.
3. **P2P Doğrudan Bağlantı**:
   - Cihaz A cevabı okuduğu an ICE kontrolü (`ICE Checking`) başlar.
   - Doğrudan STUN refleksif adresleri üzerinden bağlantı kurulur (`ICE Connected`).
   - `DataChannel` açılır (`onStateChange: OPEN`).
   - Uçtan uca AES-256-GCM anahtar değişimi tamamlanır ve anlık mesajlaşma başlar.

---

## 5. Proje ve Depo Teslimatı (GitHub Standartları)
- **AGPL-3.0 Lisansı** (`LICENSE`).
- Kapsamlı `README.md` (Mimari, P2P ilkeleri, Türkiye operatör testleri, derleme ve APK kılavuzu).
- `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY_POLICY.md`.
- Otomatik GitHub Actions derleme iş akışı (`.github/workflows/android.yml`).
- GitHub Releases son sürüm denetleyicisi (`GitHubUpdateService`).
- Orijinal logonun tüm mipmap (`hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`, `anydpi-v26`) simgelerine dönüştürülmesi ve eklenmesi.
