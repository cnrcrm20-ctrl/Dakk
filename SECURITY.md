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
- **Güncellemeler:** APK'lar her zaman aynı sabit anahtarla imzalanır; Android, farklı imzalı bir
  güncellemenin kurulmasına izin vermez.

### Neyi korumuyoruz? (bilinen sınırlamalar)
- **Meta veriler:** Kimin kiminle, ne zaman yazıştığı ve iletim durumu sunucuda şifresiz tutulur.
- **İleri gizlilik (forward secrecy) yoktur:** Bir cihazın özel anahtarı ele geçirilirse o anahtarla
  şifrelenmiş eski mesajlar da çözülebilir.
- **Ele geçirilmiş cihaz:** Kilidi açık ve kötü amaçlı yazılım bulunan bir telefondaki mesajlar
  korunamaz.
- **Profil bilgileri** (ad, e-posta, fotoğraf) giriş yapmış diğer kullanıcılara görünür.

### İmza anahtarı hakkında
Google ile girişin herkes için aynı şekilde çalışması amacıyla imza anahtarı
(`signing/dakk-release.p12`) depoda bulunur. Bu, kodu derleyen herkesin aynı imzayla APK
üretebileceği anlamına gelir. Dakk'ı **yalnızca resmi GitHub Releases sayfasından** indir.
Bakımcılar, anahtarı depo secret'larına taşıyarak (`DAKK_KEYSTORE_BASE64` vb.) bu riski kaldırabilir.

İmza parmak izleri:
- SHA-1: `05:8B:B2:98:2B:5C:20:CD:F5:CD:29:FC:68:3D:4C:CB:C4:A1:56:21`
- SHA-256: `A1:31:5B:56:6C:73:7F:46:3E:27:D8:01:36:03:47:F9:C0:80:06:85:5F:E7:66:E7:B9:6F:E1:FB:35:23:FC:D9`

Her Release notunda APK dosyasının SHA-256 özeti yer alır; indirdiğin dosyayı bununla doğrulayabilirsin.
