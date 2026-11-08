# Dakk — Sistem Hafızası (Brain)

> Bu dosya projeye dokunan herkes (insan veya yapay zekâ) için **önce okunacak** özet ve kurallardır.
> Burada yazanlar ile kod çelişirse önce bu dosyayı güncelleyin.

## Kimlik

| Alan | Değer |
|---|---|
| Uygulama | Dakk — uçtan uca şifreli mesajlaşma (Android) |
| Paket adı (applicationId) | `dakk.android.co` — **değiştirmeyin** (Firebase'e kayıtlı) |
| Kod namespace'i | `com.example` (yalnızca iç paket adı, kullanıcıya görünmez) |
| Mevcut sürüm | `2.0.0` (versionCode `20`) |
| Depo | https://github.com/cnrcrm20-ctrl/Dakk |
| Firebase proje ID | `gen-lang-client-0161098568` |
| Firestore veritabanı ID | `ai-studio-android-e2d067e8-8aba-4fce-a486-b836129f76c1` |
| Web istemci ID (Google Sign-In) | `721295735056-2b9hjum4qc2t4pjm530cn31fahtdg31j.apps.googleusercontent.com` |

## ⚠️ En önemli kural: İmza anahtarı ve SHA-1

Google ile Giriş, APK'yı imzalayan sertifikanın SHA-1 parmak izine bağlıdır. Eskiden CI her derlemede
**rastgele yeni bir debug.keystore** ürettiği için SHA-1 her sürümde değişiyor ve giriş
`10: [28444] Developer console is not set up correctly` hatası veriyordu.

Artık **tek, sabit** bir anahtar kullanılıyor:

- Dosya: `signing/dakk-release.p12` (PKCS12, alias `dakk`, parola `dakk-android-2026`)
- **SHA-1:** `05:8B:B2:98:2B:5C:20:CD:F5:CD:29:FC:68:3D:4C:CB:C4:A1:56:21`
- **SHA-256:** `A1:31:5B:56:6C:73:7F:46:3E:27:D8:01:36:03:47:F9:C0:80:06:85:5F:E7:66:E7:B9:6F:E1:FB:35:23:FC:D9`
- Hem `debug` hem `release` derlemeleri bu anahtarla imzalanır (`app/build.gradle.kts`).
- CI, üretilen APK'nın SHA-1'ini `EXPECTED_SHA1` ile karşılaştırır; farklıysa derlemeyi **durdurur**.

Bu SHA-1 ve SHA-256 değerleri Firebase Console › Proje ayarları › `dakk.android.co` Android uygulaması
bölümüne eklenmiş olmalıdır. Anahtar değişirse: tüm kullanıcılar uygulamayı silip yeniden kurmak
zorunda kalır ve Google girişi yeni SHA-1 eklenene kadar çalışmaz. **Anahtarı değiştirmeyin, silmeyin.**

Güvenlik notu: Anahtar depo içinde olduğu için depoya erişen herkes aynı imzayla APK üretebilir.
Daha sıkı güvenlik için aynı dosya `DAKK_KEYSTORE_BASE64` (+ `DAKK_KEYSTORE_PASSWORD`, `DAKK_KEY_ALIAS`,
`DAKK_KEY_PASSWORD`) GitHub Secrets'a konup depodan kaldırılabilir — iş akışı bunu otomatik destekler.
SHA-1 aynı kalır çünkü anahtar aynıdır.

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
   └─ ChatScreen            Mesajlar, okundu bilgisi, güvenlik numarası, engelle/temizle
DakkViewModel               Oturum, Firestore dinleyicileri, bildirimler, güncelleme
FirestoreRepository         Tüm Firestore okuma/yazmaları
CryptoEngine                P-256 ECDH + AES-256-GCM, özel anahtar Android Keystore ile sarılı
GitHubUpdateManager         Uygulama içi güncelleme (GitHub Releases)
NotificationHelper          Mesaj bildirimleri
util/                       InviteLinks, VersionUtils (birim testli), QrCodeHelper, AppSignature
```

### Firestore veri modeli (mevcut `firestore.rules` ile uyumlu — kural dağıtımı gerekmez)

- `users/{uid}`: `userId, displayName, email (küçük harf), photoUrl, status, publicKey, createdAt, updatedAt`
- `users/{uid}/contacts/{contactUid}`: `contactUserId, alias, email, photoUrl, isBlocked, createdAt, updatedAt`
- `chats/{chatId}` — `chatId = sıralı(uidA, uidB).join("_")`:
  `memberUids, lastMessage (şifreli önizleme), lastMessageSenderId, lastMessageSenderKey,
  lastMessageRecipientKey, lastMessageTimestamp, createdAt, updatedAt`
- `chats/{chatId}/messages/{id}`: `chatId, senderId, senderName, content (şifreli), senderKey,
  recipientKey, deliveryStatus (SENT/READ), messageType (TEXT), createdAt`

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

## Bilinen sınırlamalar / gelecek işler

- Push bildirimleri (FCM) yok; bildirimler uygulama süreci çalışırken gelir. FCM için Cloud Functions gerekir.
- Çoklu cihaz desteği yok (son giriş yapılan cihazın anahtarı geçerlidir).
- Medya (fotoğraf/ses) gönderimi yok — v1'deki sahte/yarım özellikler v2'de kaldırıldı.
- Davet bağlantısı sayfası için GitHub Pages'in `main` dalı `/docs` klasöründen yayınlanması gerekir
  (Settings › Pages). Pages kapalıyken bile QR kod ve e-posta ile ekleme çalışır.

## v2'de kaldırılanlar (geri eklemeyin, gerçek çalışmıyordu)

WebRTC P2P el sıkışma ekranı, PIN/ntfy.sh rendezvous, sahte sesli/görüntülü arama, sahte
fotoğraf/belge/konum gönderimi, yerel ses kaydı, sahte grup sohbeti, "Türkiye Operatör Test
Laboratuvarı" ağ teşhis ekranı, Gemini "Dakk Asistanı" botu, App Check (debug/reCAPTCHA),
Room veritabanı, 512 karakterlik rastgele kimlik.
