# Güvenlik Politikası

## Desteklenen sürümler

| Sürüm | Destek |
| --- | --- |
| 2.x (son sürüm) | ✅ Güvenlik düzeltmeleri yapılır |
| 1.x | ❌ Desteklenmiyor — lütfen son sürüme geçin |

Uygulama içi otomatik güncelleme açık kaldığı sürece her zaman en güncel sürümü kullanırsın.

## Güvenlik açığı bildirme

Bir güvenlik açığı bulduysan lütfen **herkese açık issue açma**.

1. GitHub deposunda **Security → Report a vulnerability** (özel güvenlik bildirimi) yolunu kullan.
2. Sorunu, yeniden üretme adımlarını ve olası etkisini açıkla.
3. Bildirimin en geç 7 gün içinde yanıtlanır; düzeltme yayınlanana kadar ayrıntıları gizli tut.

## Dakk neyi korur?

- **Mesajların:** Telefonundan çıkmadan önce uçtan uca şifrelenir. Sunucuda yalnızca okunamayan
  şifreli veri durur; mesajı sadece sen ve karşındaki kişi açabilir.
- **Anahtarın:** Telefonunda üretilir, Android Keystore ile korunur ve telefonundan hiç çıkmaz.
- **Sohbetlerin:** Yalnızca sohbetin üyeleri erişebilir.
- **Doğru kişiyle konuştuğun:** Sohbet menüsündeki *Güvenlik numarası* iki telefonda aynıysa sohbet güvenlidir.
- **Güncellemeler:** Tüm resmi sürümler aynı imzayla yayınlanır. Android, farklı imzalı sahte bir
  güncellemenin kurulmasına izin vermez.

## Bilinen sınırlamalar

- Kimin kiminle ve ne zaman yazıştığı bilgisi (meta veri) şifrelenmez.
- Bir telefonun anahtarı ele geçirilirse o telefondaki eski mesajlar da okunabilir.
- Kötü amaçlı yazılım bulaşmış, kilidi açık bir telefondaki mesajlar korunamaz.
- Profil bilgilerin (ad, e-posta, fotoğraf) Dakk kullanan diğer kişilere görünür.
- Dakk Asistan ile yapılan sohbetler uçtan uca şifreli değildir.

## İndirdiğin APK'yı doğrulama

Dakk'ı yalnızca resmi [Releases](https://github.com/cnrcrm20-ctrl/Dakk/releases/latest) sayfasından indir.
Her Release notunda APK dosyasının SHA-256 özeti yer alır.

Resmi imza parmak izi:
- SHA-256: `A1:E4:F4:6F:E3:DE:83:B8:C7:B9:DF:3F:C9:92:EC:F8:EE:17:91:6B:DE:EB:85:C1:C5:FB:41:8E:56:FB:F6:BC`
