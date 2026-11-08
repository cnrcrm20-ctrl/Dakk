# Güvenlik Politikası

## Desteklenen sürümler

| Sürüm | Destek |
| --- | --- |
| 2.x (son sürüm) | ✅ Güvenlik düzeltmeleri yapılır |
| 1.x | ❌ Desteklenmiyor — lütfen 2.x'e geçin |

Uygulama içi otomatik güncelleme açık kaldığı sürece her zaman en güncel sürümü kullanırsın.

## Güvenlik açığı bildirme

Bir güvenlik açığı bulduysan lütfen **herkese açık issue açma**.

1. GitHub deposunda **Security → Report a vulnerability** (özel güvenlik bildirimi) yolunu kullan.
2. Sorunu, yeniden üretme adımlarını ve olası etkisini açıkla.
3. Bildirimin en geç 7 gün içinde yanıtlanır; düzeltme yayınlanana kadar ayrıntıları gizli tut.

## Güvenlik modeli

### Neyi koruyoruz?
- **Mesaj içeriği:** EC P-256 ECDH ile türetilen ortak anahtar (SHA-256) ve AES-256-GCM ile cihazda
  şifrelenir. Sunucu (Firestore) ve sunucuya erişimi olan herkes yalnızca şifreli metni görür.
- **Özel anahtar:** Cihazda üretilir, Android Keystore'daki donanım destekli bir anahtarla sarılarak
  saklanır ve cihazdan hiç çıkmaz. Uygulama yedeklemeye kapalıdır.
- **Kimlik doğrulama:** Google ile giriş (Firebase Authentication). Firestore güvenlik kuralları,
  kullanıcıların yalnızca üyesi oldukları sohbetleri okuyup yazabilmesini sağlar.
- **Ortadaki adam saldırılarına karşı:** Sohbet menüsündeki *Güvenlik numarası*, iki tarafın açık
  anahtarlarından hesaplanır. Yüz yüze karşılaştırıldığında aynıysa sohbet güvenlidir.
- **Güncellemeler:** APK'lar her zaman aynı resmi anahtarla imzalanır; Android, farklı imzalı bir
  güncellemenin kurulmasına izin vermez. Uygulama içi güncelleyici de indirdiği APK'nın imzasını
  kurmadan önce kontrol eder.

### Neyi korumuyoruz? (bilinen sınırlamalar)
- **Meta veriler:** Kimin kiminle, ne zaman yazıştığı ve iletim durumu sunucuda şifresiz tutulur.
- **İleri gizlilik (forward secrecy) yoktur:** Bir cihazın özel anahtarı ele geçirilirse o anahtarla
  şifrelenmiş eski mesajlar da çözülebilir.
- **Ele geçirilmiş cihaz:** Kilidi açık ve kötü amaçlı yazılım bulunan bir telefondaki mesajlar
  korunamaz.
- **Profil bilgileri** (ad, e-posta, fotoğraf) giriş yapmış diğer kullanıcılara görünür.

### İmza anahtarı hakkında
Resmi imza anahtarı depoda yalnızca AES-256 ile **şifrelenmiş** olarak bulunur
(`signing/dakk-release.p12.enc`); şifresi sadece GitHub Actions secret'ında saklanır. Böylece kaynak
kodu herkese açık olsa da kimse resmi Dakk imzasıyla APK üretemez.

2.0.x sürümlerinde kullanılan eski anahtar depoda açık durduğu için 2.1.0 ile **emekliye ayrılmıştır**.
Eski imzalı (2.0.x) APK'lara artık güvenmeyin. Dakk'ı yalnızca resmi GitHub Releases sayfasından indir.

Resmi imza parmak izleri (2.1.0 ve sonrası):
- SHA-1: `1B:8A:A6:D1:88:69:43:31:9A:70:65:4F:A6:DA:DC:7D:F9:E3:5A:E1`
- SHA-256: `A1:E4:F4:6F:E3:DE:83:B8:C7:B9:DF:3F:C9:92:EC:F8:EE:17:91:6B:DE:EB:85:C1:C5:FB:41:8E:56:FB:F6:BC`

Her Release notunda APK dosyasının SHA-256 özeti yer alır; indirdiğin dosyayı bununla doğrulayabilirsin.
