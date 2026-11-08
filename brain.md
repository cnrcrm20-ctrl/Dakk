# Dakk — Sistem Hafızası (Brain)

> Bu dosya projeye dokunan herkes (insan veya yapay zekâ) için **önce okunacak** özet ve kurallardır.
> Burada yazanlar ile kod çelişirse önce bu dosyayı güncelleyin.

## Kimlik

| Alan | Değer |
|---|---|
| Uygulama | Dakk — uçtan uca şifreli mesajlaşma (Android) |
| Paket adı (applicationId) | `dakk.android.co` — **değiştirmeyin** (Firebase'e kayıtlı) |
| Kod namespace'i | `com.example` (yalnızca iç paket adı, kullanıcıya görünmez) |
| Mevcut sürüm | `2.1.0` (versionCode `22`) |
| Depo | https://github.com/cnrcrm20-ctrl/Dakk |
| Firebase proje ID | `gen-lang-client-0161098568` (Cloud Console'daki adı: "dakk messanger") |
| Firebase Android uygulaması | `1:721295735056:android:f275568d4fe640156a8d95` (`dakk.android.co`) |
| Firestore veritabanı ID | `ai-studio-android-e2d067e8-8aba-4fce-a486-b836129f76c1` |
| Web istemci ID (Google Sign-In) | `721295735056-2b9hjum4qc2t4pjm530cn31fahtdg31j.apps.googleusercontent.com` |

## ⚠️ En önemli kural: İmza anahtarı ve SHA-1

Google ile Giriş, APK'yı imzalayan sertifikanın SHA-1 parmak izine bağlıdır. Eskiden CI her derlemede
**rastgele yeni bir debug.keystore** ürettiği için SHA-1 her sürümde değişiyor ve giriş
`10: [28444] Developer console is not set up correctly` hatası veriyordu.

Artık **tek, sabit** bir resmi anahtar kullanılıyor (2.1.0'dan itibaren):

- Depoda yalnızca şifreli hali var: `signing/dakk-release.p12.enc`
  (OpenSSL `aes-256-cbc -pbkdf2 -iter 600000`; içi PKCS12, alias `dakk`).
- Şifre = GitHub secret **`DAKK_SIGNING_PASSPHRASE`** (aynı zamanda keystore ve anahtar parolası).
  Bu şifre kaybolursa resmi anahtar da kaybolur → kullanıcılar bir daha güncelleme alamaz. Şifre bakımcıda
  güvenli bir yerde yedekli durmalıdır.
- **SHA-1:** `1B:8A:A6:D1:88:69:43:31:9A:70:65:4F:A6:DA:DC:7D:F9:E3:5A:E1`
- **SHA-256:** `A1:E4:F4:6F:E3:DE:83:B8:C7:B9:DF:3F:C9:92:EC:F8:EE:17:91:6B:DE:EB:85:C1:C5:FB:41:8E:56:FB:F6:BC`
- CI, "İmza anahtarını çöz" adımında anahtarı `$RUNNER_TEMP`'e çözer ve `DAKK_KEYSTORE_PATH`,
  `DAKK_KEYSTORE_PASSWORD`, `DAKK_KEY_ALIAS`, `DAKK_KEY_PASSWORD` ortam değişkenlerini verir.
  `app/build.gradle.kts` bu değişkenler varsa resmi anahtarla, yoksa debug anahtarıyla imzalar.
- CI, üretilen APK'nın SHA-1'ini `EXPECTED_SHA1` ile karşılaştırır; farklıysa derlemeyi **durdurur**.
  Secret yoksa PR derlemeleri debug imzalı olur ve Release oluşturulmaz; `main` derlemesi hata verir.

Bu SHA-1 ve SHA-256 değerleri Firebase Console › Proje ayarları › `dakk.android.co` Android uygulaması
bölümüne eklenmiş olmalıdır. Anahtar değişirse tüm kullanıcılar uygulamayı silip yeniden kurmak zorunda
kalır ve Google girişi yeni SHA-1 eklenene kadar çalışmaz. **Anahtarı değiştirmeyin, silmeyin.**

Geçmiş: 2.0.0–2.0.1 sürümleri depoda açık duran `signing/dakk-release.p12` ile imzalanmıştı
(SHA-1 `05:8B:B2:98:…:A1:56:21`). Bu anahtar 2.1.0'da emekliye ayrıldı; Firebase'den de kaldırılmalı.
Uygulama içi güncelleyici, imzası farklı bir APK indirirse kurmayı denemez, "Yeniden kurulum gerekiyor"
penceresini gösterir.

## Google ile Giriş yapılandırması (çalışır durumda — bozmayın)

- Google Cloud › Google Auth Platform › **Audience: In production**, User type: External.
  Testing moduna dönülürse yalnızca test kullanıcıları giriş yapabilir (diğerleri hesap seçtikten sonra
  giriş ekranına geri atılır: `[16] Account reauth failed`).
- Branding: uygulama adı Dakk; ana sayfa / gizlilik / koşullar bağlantıları github.com'u gösterir;
  yetkili alan adları: `gen-lang-client-0161098568.firebaseapp.com`, `github.com`. **Logo yüklemeyin**
  (Google doğrulaması gerektirir).
- Data Access: yalnızca `openid`, `userinfo.email`, `userinfo.profile` (hassas olmayan; doğrulama gerekmez).
- `default_web_client_id`: `721295735056-2b9hjum4qc2t4pjm530cn31fahtdg31j.apps.googleusercontent.com`
- `GoogleAuth.signIn` önce "Google ile oturum aç" düğme akışını, başarısız olursa `GetGoogleIdOption`
  akışını dener; yapılandırma hatalarını (`AuthErrorClassifier`) ekranda SHA-1 ile birlikte gösterir.

## Açılış animasyonu

- 30 kare (`res/drawable-nodpi/dakk_splash_00..29.webp`, 360×640, zemin `#FFFEFE`), 100 ms/kare.
- `ui/components/DakkSplashAnimation.kt`; `MainActivity` yalnızca normal açılışta oynatır
  (bildirim/davet bağlantısıyla açılışta atlanır), dokununca geçilir. Animasyon sırasında diyaloglar ertelenir.
- Manifest teması `Theme.Dakk.Launch` (beyaz pencere, Android 12+ simgesiz sistem açılış ekranı);
  `onCreate` içinde `Theme.Dakk`'a geçilir.

## Sürüm yayınlama (Release) akışı

1. `app/build.gradle.kts` içinde `dakkVersionName` ve `dakkVersionCode` değerlerini artırın.
2. `CHANGELOG.md` dosyasına `## [x.y.z] - YYYY-AA-GG` başlığıyla notları ekleyin.
3. `main` dalına push edin (veya PR'ı birleştirin).
4. `.github/workflows/android.yml` → test → `assembleRelease` → imza doğrulama →
   `vX.Y.Z` için Release yoksa oluşturur ve `Dakk-vX.Y.Z.apk` dosyasını yükler.
5. Uygulama açılışta GitHub `releases/latest` uç noktasını kontrol eder ve yeni sürümü indirip kurmayı önerir.

Aynı sürüm numarasıyla tekrar push edilirse yeni Release oluşturulmaz (yalnızca artifact üretilir).

## Mimari (v2)

```
MainActivity ── DakkApp (Compose)
   ├─ AuthScreen            Google ile giriş (Credential Manager + Firebase Auth)
   ├─ HomeScreen            Sohbetler / Kişiler / Ayarlar sekmeleri
   │    └─ AddContactSheet  QR tara · galeriden QR · QR kodum · davet bağlantısı · e-posta ile ara
   ├─ ChatScreen            Mesajlar, fotoğraflar, @dakk, okundu bilgisi, güvenlik numarası
   ├─ AssistantScreen       Dakk Asistan sohbeti
   ├─ FounderPanelScreen    Kurucu paneli (şema buluttan gelir)
   └─ Tam ekran uyarılar    Bakım > Zorunlu güncelleme > Duyuru > Güncelleme penceresi
DakkViewModel               Oturum, Firestore dinleyicileri, bildirimler, güncelleme, asistan, fotoğraf
FirestoreRepository         Tüm Firestore okuma/yazmaları
ConfigRepository            app_config/* (public, private, admin_panel)
MediaManager                Fotoğraf sıkıştırma + şifreli parça yükleme/indirme + 7 gün temizliği
AssistantEngine             Gemini (Firebase AI Logic) / özel API / sunucu vekili; yerel geçmiş
PushClient, DakkMessagingService   FCM jetonu, sunucuya bildirim isteği, kapalı uygulamada bildirim
CryptoEngine                P-256 ECDH + AES-256-GCM, özel anahtar Android Keystore ile sarılı
GitHubUpdateManager         Uygulama içi güncelleme (GitHub Releases)
NotificationHelper          Mesaj bildirimleri
util/                       InviteLinks, VersionUtils (birim testli), QrCodeHelper, AppSignature
```

### Firestore veri modeli (`firestore.rules` 2.1.0'da güncellendi — Firebase Console'a elle yapıştırılır)

- `users/{uid}`: `userId, displayName, email (küçük harf), photoUrl, status, publicKey, createdAt, updatedAt`
- `users/{uid}/contacts/{contactUid}`: `contactUserId, alias, email, photoUrl, isBlocked, createdAt, updatedAt`
- `chats/{chatId}` — `chatId = sıralı(uidA, uidB).join("_")`:
  `memberUids, lastMessage (şifreli önizleme), lastMessageSenderId, lastMessageSenderKey,
  lastMessageRecipientKey, lastMessageTimestamp, createdAt, updatedAt`
- `chats/{chatId}/messages/{id}`: `chatId, senderId, senderName, content (şifreli), senderKey,
  recipientKey, deliveryStatus (SENT/READ), messageType (TEXT/IMAGE), isAssistant?, createdAt`
  - IMAGE mesajında `content` = şifreli JSON `{v,type:"image",mediaId,chunks,mime,w,h,size,thumb}`
- `chats/{chatId}/blobs/{mediaId}_{i}`: `mediaId, index, total (≤4), senderId, data (şifreli, Base64, ≤950k), createdAt`
  — alıcı indirince siler; gönderici 7 gün sonra siler (yerel liste: prefs `dakk_media/uploads`).
- `app_config/public` (herkes okur, kurucu yazar): `assistant_*`, `images_enabled`, `min_supported_version`,
  `force_update_message`, `maintenance_*`, `server_url`, `announcement{id,active,title,body,image_url,button_text,
  button_url,start_at,end_at,min_version,dismissible}`
- `app_config/private` (yalnızca kurucu): `custom_api_base_url, custom_api_key, custom_api_model`
- `app_config/admin_panel` (yalnızca kurucu): `schema` (cloud/admin_panel.json metni)
- `push_tokens/{uid}` (yalnızca sahibi yazar, kimse okumaz): `token, platform, updatedAt`

Önemli kural ayrıntıları:
- Mesaj yazmadan **önce** sohbet belgesi yazılmalı (kural `get(chat).memberUids` kontrol eder).
  Yazmalar sırayla kuyruğa alındığı için `FirestoreRepository.sendMessage` bunu garanti eder.
- Var olmayan bir sohbetin mesajları dinlenemez (izin hatası). ViewModel, sohbet belgesi
  `chats` sorgusunda görünene kadar mesaj dinleyicisini başlatmaz ve hatada yeniden dener.
- `content` ≤ 5000 karakter → düz metin en fazla 3500 bayt (`MAX_MESSAGE_BYTES`).

### Şifreleme

- Her cihaz bir EC P-256 anahtar çifti üretir; açık anahtar `users/{uid}.publicKey` alanında yayınlanır.
- Mesaj anahtarı = SHA-256(ECDH(benimÖzel, karşıAçık)); şifre AES-256-GCM (12 bayt IV).
- Her mesaj, gönderen ve alıcının o anki açık anahtarlarını (`senderKey`, `recipientKey`) taşır.
- Uygulama yeniden kurulursa veya başka cihazda giriş yapılırsa yeni anahtar üretilir; eski mesajlar
  o cihazda "çözülemedi" olarak görünür (bilinçli tasarım — sunucu anahtarları asla görmez).
- Güvenlik numarası: iki açık anahtarın SHA-512 özetinden 60 haneli sayı.

## Kurucu paneli, duyurular, asistan, fotoğraf, bildirim (2.1.0)

- **Kurucular:** thecukulataa@gmail.com ve cnrcrm20@gmail.com. Liste YALNIZCA `firestore.rules` → `isFounder()`
  içindedir (email_verified şart). Uygulama, `app_config/private` okunabiliyorsa kullanıcıyı kurucu sayar.
  Kurucu eklemek/çıkarmak = kuralları değiştirip Firebase Console'da yayınlamak.
- **Panel buluttan gelir:** `cloud/admin_panel.json` → panelde "Paneli kur / GitHub'dan güncelle" ile
  `app_config/admin_panel.schema` alanına yazılır. Alan türleri: switch, text, multiline, secret, number,
  select, url, version, announcement, stat (`user_count`), note. Yeni ayar = JSON'a alan ekle +
  `PublicConfig.fromMap`'te oku (uygulamanın kullanması için sürüm gerekir; yalnızca panelde görünmesi için gerekmez).
- **Öncelik:** Bakım (kurucular muaf) > Zorunlu güncelleme (`min_supported_version`) > Duyuru > Güncelleme penceresi.
  Duyuru her `id` için bir kez gösterilir (prefs `seen_announcements`); "Yayınla" her basışta yeni id üretir.
- **Dakk Asistan:** Varsayılan Firebase AI Logic + Gemini Developer API (ücretsiz, uygulamada anahtar yok).
  Firebase Console › AI Logic › Gemini Developer API etkinleştirilmiş olmalı. Arayüzde "Google" yazmaz;
  uyarı metni "Bu mesaj ve sohbet yapay zekâ tarafından işlenecek" + Ayrıntılar (gizlilik politikası sağlayıcıyı adlandırır).
  Özel API (OpenAI uyumlu): anahtar `app_config/private`'ta; kullanıcılar için Worker `/assistant` vekil olur,
  kurucular doğrudan çağırır. Günlük sınır istemci tarafında (kurucular muaf).
  `@dakk` → son 30 mesaj bağlam olarak gider; yanıt `isAssistant:true` TEXT mesajı olarak çağıranın hesabından yazılır.
  Gelecek: seçenek C — amiral gemisi telefon/tabletlerde Gemini Nano (cihaz içi).
- **Fotoğraf:** Firebase Storage Blaze ister → fotoğraflar Firestore'da şifreli parçalar (≤700 KB ham/parça).
  1600 px, JPEG q80 (≤1.2 MB), 40 px bulanık önizleme mesajın içinde. Video yok (kullanıcı kararı).
- **Kapalı uygulamada bildirim:** Gönderen cihaz, mesaj sunucuya ulaşınca `server_url/notify`'a
  {chatId, messageId} + Firebase ID token yollar. Worker (`cloud/worker/dakk-worker.js`) üyeliği, göndereni ve
  engeli doğrular, `push_tokens/{alıcı}` ile FCM v1 data mesajı (şifreli içerik) gönderir. Telefonda
  `DakkMessagingService` çözer. Uygulama ön plandaysa servis bildirim göstermez; arka plandaysa ve push etkinse
  ViewModel yerel bildirimi atlar (çift bildirim olmasın). Worker, sahte Google servisleriyle uçtan uca test edildi.
  Kurulum: `cloud/worker/README.md` (Cloudflare ücretsiz; secret `FIREBASE_SERVICE_ACCOUNT`).
- Kritik duyurular için push şimdilik YOK (kullanıcı kararı).

## Bilinen sınırlamalar / gelecek işler

- Çoklu cihaz desteği yok (son giriş yapılan cihazın anahtarı geçerlidir).
- Video, sesli mesaj ve grup sohbeti yok.
- Davet bağlantısı sayfası için GitHub Pages'in `main` dalı `/docs` klasöründen yayınlanması gerekir
  (Settings › Pages). Pages kapalıyken bile QR kod ve e-posta ile ekleme çalışır.

## v2'de kaldırılanlar (geri eklemeyin, gerçek çalışmıyordu)

WebRTC P2P el sıkışma ekranı, PIN/ntfy.sh rendezvous, sahte sesli/görüntülü arama, sahte
fotoğraf/belge/konum gönderimi, yerel ses kaydı, sahte grup sohbeti, "Türkiye Operatör Test
Laboratuvarı" ağ teşhis ekranı, v1'in sahte Gemini botu (2.1.0'da gerçek Dakk Asistan olarak yeniden yazıldı), App Check (debug/reCAPTCHA),
Room veritabanı, 512 karakterlik rastgele kimlik.
