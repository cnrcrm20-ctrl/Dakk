# Dakk Gizlilik Politikası

**Son güncelleme:** 8 Kasım 2026 • **Geçerli sürüm:** Dakk 2.0 ve sonrası

Dakk, açık kaynaklı bir mesajlaşma uygulamasıdır. Bu metin, uygulamanın hangi verileri neden
kullandığını sade bir dille açıklar. Kaynak kodun tamamı herkese açıktır; burada yazanları
[GitHub deposunda](https://github.com/cnrcrm20-ctrl/Dakk) doğrulayabilirsin.

## Kısaca

- Mesajlarının **içeriğini** biz dahil kimse okuyamaz; cihazında şifrelenir.
- Telefon numarası, rehber, konum, mikrofon veya fotoğraf erişimi **istemeyiz**.
- Reklam, analiz veya izleme aracı **kullanmayız**. Verilerini satmayız, paylaşmayız.

## Topladığımız veriler

### 1. Hesap bilgileri (Google ile giriş)
Google ile giriş yaptığında Firebase Authentication üzerinden şu bilgiler alınır:
- Ad (görünen ad), e-posta adresi ve profil fotoğrafı bağlantısı
- Firebase tarafından üretilen kullanıcı kimliği (UID)

Bu bilgiler Cloud Firestore'daki **profilinde** saklanır ve Dakk'a giriş yapmış diğer kullanıcılar
tarafından görülebilir. Bunun nedeni, arkadaşlarının seni e-posta adresinle veya davet bağlantınla
bulabilmesidir. Görünen adını Ayarlar'dan değiştirebilirsin.

### 2. Şifreleme açık anahtarı
Cihazın bir anahtar çifti üretir. **Açık anahtar** profilinde yayınlanır (başkalarının sana şifreli
mesaj gönderebilmesi için gereklidir). **Özel anahtar** cihazından hiç çıkmaz ve Android Keystore ile
korunur.

### 3. Mesajlar
- Mesaj içeriği ve sohbet listesinde görünen son mesaj önizlemesi **uçtan uca şifreli** olarak
  Firestore'da saklanır. Sunucuda yalnızca okunamayan şifreli metin bulunur.
- Mesajın iletilebilmesi için şu bilgiler **şifrelenmeden** saklanır: gönderen ve alıcı kimliği,
  gönderim zamanı, iletim/okundu durumu ve gönderenin görünen adı.

### 4. Kişi listesi
Eklediğin kişiler (kimlik, ad, e-posta, fotoğraf bağlantısı, engelli olup olmadığı) yalnızca senin
okuyabildiğin bir alanda saklanır.

### 5. Cihazda tutulanlar
Ayarların (ör. otomatik güncelleme açık/kapalı) ve şifreli özel anahtarın yalnızca cihazında durur.
Uygulama yedeklemeye kapalıdır; bu veriler Google yedeğine aktarılmaz.

## İzinler

| İzin | Neden |
| --- | --- |
| İnternet / ağ durumu | Mesaj göndermek ve almak |
| Kamera | Yalnızca QR kod tararken; görüntü kaydedilmez veya gönderilmez |
| Bildirimler | Yeni mesajları haber vermek |
| Uygulama yükleme | Uygulama içi güncellemeyi kurmak (yalnızca sen onaylarsan) |

## Üçüncü taraf hizmetler

- **Google Firebase** (Authentication, Cloud Firestore) — hesap ve şifreli mesajların saklanması.
  Google'ın gizlilik politikası: <https://policies.google.com/privacy>
- **GitHub** — güncelleme denetimi için GitHub Releases'a anonim bir istek gönderilir ve APK
  buradan indirilir. GitHub'ın gizlilik politikası:
  <https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement>

## Verilerini silmek

- **Mesaj:** Gönderdiğin bir mesaja uzun basıp *Herkesten sil* diyerek sunucudan kaldırabilirsin.
- **Sohbet:** *Sohbeti temizle* mesajları yalnızca kendi cihazındaki görünümden kaldırır.
- **Hesap:** Hesabının ve tüm verilerinin silinmesini istiyorsan GitHub deposunda
  "Hesap silme talebi" başlıklı bir issue aç (e-posta adresini issue'ya **yazma**); seninle
  iletişime geçilerek talebin işleme alınır.

## Çocuklar

Dakk 13 yaşından küçük çocuklara yönelik değildir.

## Değişiklikler

Bu politika güncellendiğinde değişiklikler bu dosyada ve [CHANGELOG.md](CHANGELOG.md)'de yer alır.
