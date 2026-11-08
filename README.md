# Dakk — Uçtan Uca Şifreli Mesajlaşma

<p align="center">
  <img src="app/src/main/res/drawable/dakk_logo.png" width="140" alt="Dakk logosu" />
</p>

<p align="center">
  <strong>Google hesabınla gir • QR kodla arkadaş ekle • Mesajların yalnızca sizde okunur</strong>
</p>

<p align="center">
  <a href="https://github.com/cnrcrm20-ctrl/Dakk/releases/latest"><img alt="Son sürüm" src="https://img.shields.io/github/v/release/cnrcrm20-ctrl/Dakk?label=s%C3%BCr%C3%BCm&color=FFC107"></a>
  <a href="https://github.com/cnrcrm20-ctrl/Dakk/actions/workflows/android.yml"><img alt="Derleme" src="https://github.com/cnrcrm20-ctrl/Dakk/actions/workflows/android.yml/badge.svg"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84">
  <a href="LICENSE"><img alt="Lisans" src="https://img.shields.io/badge/lisans-AGPL--3.0-blue"></a>
</p>

---

## Dakk nedir?

Dakk, arkadaşlarınla güvenle yazışman için tasarlanmış sade bir Android mesajlaşma uygulamasıdır.
Telefon numarası istemez, rehberini okumaz. Google hesabınla giriş yaparsın, arkadaşını QR kodla
veya bir bağlantıyla eklersin ve yazışmaya başlarsın.

Mesajların telefonundan çıkmadan önce şifrelenir. Sunucuda yalnızca okunamayan şifreli veri durur;
mesajı sadece sen ve karşındaki kişi açabilir.

## Özellikler

- 🔐 **Uçtan uca şifreleme** — Her mesaj cihazında şifrelenir; anahtarın Android Keystore ile korunur.
- 📨 **Çevrimdışı iletim** — Arkadaşın o an çevrimdışı olsa bile mesajın ona ulaşır.
- ✓✓ **Okundu bilgisi** — Gönderiliyor, gönderildi ve okundu göstergeleri.
- 📷 **Kolay arkadaş ekleme** — QR kod tara, galeriden QR seç, davet bağlantısı paylaş veya e-postayla ara.
- 🛡️ **Güvenlik numarası** — Sohbetin gerçekten doğru kişiyle olduğunu yüz yüze doğrula.
- 🚫 **Engelleme** — İstemediğin kişilerden gelen mesajları durdur.
- 🖼️ **Fotoğraf gönderme** — Fotoğraflar telefonda küçültülür ve uçtan uca şifrelenir. Alıcı açınca sunucudaki kopya silinir.
- ✨ **Dakk Asistan** — Uygulama içi yapay zekâ asistanı. Bir sohbette `@dakk` yazarak onu sohbete çağırabilirsin. Yapay zekâ tarafından işlenir; ilk kullanımda uyarı gösterilir.
- 🗑️ **Herkesten sil** — Gönderdiğin mesajı iki taraftan da kaldır.
- 🔔 **Bildirimler** — Yeni mesaj geldiğinde, uygulama kapalıyken bile haber verir. Bildirime dokununca sohbet açılır.
- 📣 **Duyurular** — Önemli haberler açılışta tam ekran gösterilir.
- ⬆️ **Otomatik güncelleme** — Yeni sürüm çıktığında uygulama içinden tek dokunuşla güncelle.
- 🌙 **Koyu tema** — Göz yormayan, OLED dostu tasarım.

## Kurulum

1. [Son sürüm sayfasından](https://github.com/cnrcrm20-ctrl/Dakk/releases/latest) `Dakk-vX.Y.Z.apk`
   dosyasını indir.
2. Telefonun sorarsa **bilinmeyen kaynaklardan yüklemeye** izin ver.
3. APK'yı aç ve **Yükle**'ye dokun.
4. Dakk'ı aç, **Google ile devam et**'e dokun.

Sonraki güncellemeler uygulamanın içinden gelir; yeniden APK indirmen gerekmez.

> **1.x sürümünden geçiyorsan:** 2.0 farklı bir imza anahtarıyla imzalandığı için eski uygulamayı
> bir kez **kaldırıp** yeni sürümü kurman gerekir. Bu yalnızca bir kereye mahsustur.

## Nasıl kullanılır?

| Ne yapmak istiyorsun? | Nasıl? |
| --- | --- |
| Arkadaş eklemek | Ana ekranda **Kişi ekle** → *QR kod tara*, *Galeriden QR seç* veya e-posta / davet bağlantısı yaz |
| Kendi kodunu göstermek | **Kişi ekle** → *QR kodumu göster* (veya Ayarlar → *Davet bağlantısı paylaş*) |
| Sohbeti doğrulamak | Sohbet → ⋮ → *Güvenlik numarası* — iki telefonda aynı sayılar görünmeli |
| Bir kişiyi engellemek | Sohbet → ⋮ → *Engelle* |
| Mesajı silmek | Mesaja uzun bas → *Herkesten sil* |
| Fotoğraf göndermek | Sohbet → mesaj kutusunun solundaki 🖼️ simgesi |
| Asistanla konuşmak | Sohbetler listesinin en üstündeki *Dakk Asistan* |
| Asistanı sohbete çağırmak | Mesajına `@dakk` ile başla, ör. `@dakk bu akşam ne yesek?` |
| Adını değiştirmek | Ayarlar → *Görünen ad* → *Adı düzenle* |
| Güncellemeyi elle kontrol etmek | Ayarlar → *Güncellemeleri denetle* |

## Bilinen sınırlamalar

- Uygulama kapalıyken bildirim için Dakk sunucusunun kurulu olması gerekir ([cloud/worker](cloud/worker/README.md)).
  Xiaomi/Redmi/POCO gibi telefonlarda Dakk için **Otomatik başlatma** açık olmalıdır.
- Bir hesap aynı anda tek cihazda kullanılabilir. Yeni bir cihazda giriş yapıldığında eski cihazdaki
  mesajlar o cihazda okunamaz hale gelir (anahtarlar cihazdan dışarı çıkmaz).
- Şimdilik yazı ve fotoğraf gönderilebilir. Video, sesli mesaj ve grup sohbeti yoktur.
- Dakk Asistan ile yapılan sohbetler ve `@dakk` ile asistana gönderilen mesajlar uçtan uca şifreli değildir.

## Gizlilik ve güvenlik

- Ayrıntılar: [Gizlilik Politikası](PRIVACY_POLICY.md) • [Güvenlik Politikası](SECURITY.md)
- Güvenlik açığı bildirmek için lütfen herkese açık issue açma; `SECURITY.md`'deki yolu izle.

---

## Geliştiriciler için

### Teknolojiler

| Alan | Kullanılan |
| --- | --- |
| Dil / UI | Kotlin, Jetpack Compose, Material 3 |
| Kimlik | Firebase Authentication + Credential Manager (Google ile giriş) |
| Veri | Cloud Firestore (şifreli mesajlar, şifreli fotoğraf parçaları, herkese açık profil, uygulama ayarları) |
| Bildirim | Firebase Cloud Messaging + Cloudflare Worker ([cloud/worker](cloud/worker/README.md)) |
| Yapay zekâ | Firebase AI Logic (Gemini) veya kurucu panelinden OpenAI uyumlu özel API |
| Şifreleme | EC P-256 ECDH + SHA-256 + AES-256-GCM, özel anahtar Android Keystore ile sarılı |
| QR | ZXing (üretme + çözme), CameraX (tarama) |
| Güncelleme | GitHub Releases API + `PackageInstaller` |

### Proje yapısı

```
app/src/main/java/com/example/
├── assistant/     Dakk Asistan (Gemini / OpenAI uyumlu API)
├── auth/          Google ile giriş (Credential Manager)
├── config/        Uzak ayarlar, duyurular, Kurucu paneli verisi
├── crypto/        Uçtan uca şifreleme motoru
├── data/          Modeller ve Firestore deposu
├── media/         Fotoğraf sıkıştırma, şifreli yükleme/indirme
├── notification/  Bildirim kanalları ve gösterimi
├── push/          FCM bildirim servisi ve sunucu istemcisi
├── update/        GitHub Releases tabanlı otomatik güncelleme
├── util/          Davet bağlantıları, QR, sürüm karşılaştırma, imza bilgisi
└── ui/            ViewModel, ekranlar ve bileşenler
```

```
cloud/
├── admin_panel.json   Kurucu panelinin tanımı (uygulama bu dosyayı çizer)
└── worker/            Dakk sunucusu: bildirimler + özel API vekili (Cloudflare Worker)
firestore.rules        Firestore güvenlik kuralları (kurucu listesi burada)
```

Proje hafızası ve kararların tamamı için [brain.md](brain.md) dosyasına bak.

### Derleme

```bash
./gradlew testDebugUnitTest   # birim testleri
./gradlew assembleRelease     # imzalı APK: app/build/outputs/apk/release/
```

JDK 17 ve Android SDK gerekir.

### İmza anahtarı (SHA-1) — önemli

Google ile girişin çalışması için APK'nın imza parmak izinin Firebase Console'da kayıtlı olması gerekir.
Bu yüzden resmi sürümlerin hepsi **aynı anahtarla** imzalanır. Anahtar repoda yalnızca **şifreli** durur
(`signing/dakk-release.p12.enc`). Şifre, GitHub'da `DAKK_SIGNING_PASSPHRASE` secret'ı olarak saklanır;
GitHub Actions anahtarı derleme sırasında çözer.

| | Değer |
| --- | --- |
| SHA-1 | `1B:8A:A6:D1:88:69:43:31:9A:70:65:4F:A6:DA:DC:7D:F9:E3:5A:E1` |
| SHA-256 | `A1:E4:F4:6F:E3:DE:83:B8:C7:B9:DF:3F:C9:92:EC:F8:EE:17:91:6B:DE:EB:85:C1:C5:FB:41:8E:56:FB:F6:BC` |

- CI her derlemede APK imzasını kontrol eder; SHA-1 farklıysa derleme başarısız olur.
- Secret'a erişimi olmayan derlemeler (yerel geliştirme, fork'lardan gelen PR'lar) Android debug anahtarıyla
  imzalanır. Bu APK'lar çalışır ama Google ile giriş hata verir.
- Yerelde resmi anahtarla derlemek için (yalnızca bakımcılar):
  ```bash
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in signing/dakk-release.p12.enc -out /tmp/dakk.p12
  DAKK_KEYSTORE_PATH=/tmp/dakk.p12 DAKK_KEYSTORE_PASSWORD='<parola>' ./gradlew assembleRelease
  ```

### Yeni sürüm yayınlamak

1. `app/build.gradle.kts` içinde `dakkVersionName` ve `dakkVersionCode` değerlerini artır.
2. `CHANGELOG.md` dosyasına `## [X.Y.Z] - YYYY-MM-DD` başlıklı bir bölüm ekle.
3. `main` dalına gönder. GitHub Actions APK'yı derler, imzayı doğrular ve `vX.Y.Z` Release'ini
   `Dakk-vX.Y.Z.apk` ile otomatik oluşturur. Kurulu uygulamalar güncellemeyi kendiliğinden bulur.

Katkı kuralları için [CONTRIBUTING.md](CONTRIBUTING.md).

## Lisans

[GNU AGPL-3.0](LICENSE)
