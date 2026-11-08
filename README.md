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
- 🗑️ **Herkesten sil** — Gönderdiğin mesajı iki taraftan da kaldır.
- 🔔 **Bildirimler** — Yeni mesaj geldiğinde haber verir; bildirime dokununca sohbet açılır.
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
| Adını değiştirmek | Ayarlar → *Görünen ad* → *Adı düzenle* |
| Güncellemeyi elle kontrol etmek | Ayarlar → *Güncellemeleri denetle* |

## Bilinen sınırlamalar

- Bildirimler uygulama açıkken veya arka planda çalışırken gelir; uygulama tamamen kapatıldığında
  bildirim gelmez.
- Bir hesap aynı anda tek cihazda kullanılabilir. Yeni bir cihazda giriş yapıldığında eski cihazdaki
  mesajlar o cihazda okunamaz hale gelir (anahtarlar cihazdan dışarı çıkmaz).
- Şimdilik yalnızca metin mesajları desteklenir.

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
| Veri | Cloud Firestore (yalnızca şifreli mesaj ve herkese açık profil) |
| Şifreleme | EC P-256 ECDH + SHA-256 + AES-256-GCM, özel anahtar Android Keystore ile sarılı |
| QR | ZXing (üretme + çözme), CameraX (tarama) |
| Güncelleme | GitHub Releases API + `PackageInstaller` |

### Proje yapısı

```
app/src/main/java/com/example/
├── auth/          Google ile giriş (Credential Manager)
├── crypto/        Uçtan uca şifreleme motoru
├── data/          Modeller ve Firestore deposu
├── notification/  Bildirim kanalları ve gösterimi
├── update/        GitHub Releases tabanlı otomatik güncelleme
├── util/          Davet bağlantıları, QR, sürüm karşılaştırma, imza bilgisi
└── ui/            ViewModel, ekranlar ve bileşenler
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
Bu yüzden tüm derlemeler (debug + release, yerel + CI) **aynı sabit anahtarla** imzalanır:
`signing/dakk-release.p12`.

| | Değer |
| --- | --- |
| SHA-1 | `05:8B:B2:98:2B:5C:20:CD:F5:CD:29:FC:68:3D:4C:CB:C4:A1:56:21` |
| SHA-256 | `A1:31:5B:56:6C:73:7F:46:3E:27:D8:01:36:03:47:F9:C0:80:06:85:5F:E7:66:E7:B9:6F:E1:FB:35:23:FC:D9` |

CI her derlemede APK imzasını kontrol eder; SHA-1 farklıysa derleme başarısız olur.
Anahtarı istersen repo secret'larına taşıyabilirsin (`DAKK_KEYSTORE_BASE64`, `DAKK_KEYSTORE_PASSWORD`,
`DAKK_KEY_ALIAS`, `DAKK_KEY_PASSWORD`); aynı anahtar olduğu sürece SHA-1 değişmez.

### Yeni sürüm yayınlamak

1. `app/build.gradle.kts` içinde `dakkVersionName` ve `dakkVersionCode` değerlerini artır.
2. `CHANGELOG.md` dosyasına `## [X.Y.Z] - YYYY-MM-DD` başlıklı bir bölüm ekle.
3. `main` dalına gönder. GitHub Actions APK'yı derler, imzayı doğrular ve `vX.Y.Z` Release'ini
   `Dakk-vX.Y.Z.apk` ile otomatik oluşturur. Kurulu uygulamalar güncellemeyi kendiliğinden bulur.

Katkı kuralları için [CONTRIBUTING.md](CONTRIBUTING.md).

## Lisans

[GNU AGPL-3.0](LICENSE)
