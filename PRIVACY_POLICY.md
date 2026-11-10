# Dakk Gizlilik Politikası

**Son güncelleme:** 10 Kasım 2026 • **Geçerli sürüm:** Dakk 2.1 ve sonrası

Dakk, açık kaynaklı bir mesajlaşma uygulamasıdır. Bu metin, uygulamanın hangi verileri neden kullandığını sade bir dille açıklar. Kaynak kodun tamamı herkese açıktır. Burada yazanları [GitHub deposunda](https://github.com/cnrcrm20-ctrl/Dakk) doğrulayabilirsin.

## Kısaca

- Mesajlarının ve fotoğraflarının **içeriğini** biz dahil kimse okuyamaz, çünkü cihazında şifrelenirler.
- **Dakk Asistan** ile yaptığın sohbetler ve bir sohbette **@dakk** yazarak asistanı çağırdığın mesajlar yapay zekâ tarafından işlenir. Bu sohbetler uçtan uca şifreli değildir. Ayrıntılar aşağıda.
- Şunlara erişim **istemeyiz**: telefon numarası, rehber, konum, mikrofon. Fotoğraflarına yalnızca gönderdiğin fotoğrafı kendin seçtiğinde erişilir.
- Reklam, analiz veya izleme aracı **kullanmayız**. Verilerini satmayız, paylaşmayız.

## Topladığımız veriler

### 1. Hesap bilgileri (Google ile giriş)
Google ile giriş yaptığında Firebase Authentication üzerinden şu bilgiler alınır:
- Ad (görünen ad), e-posta adresi ve profil fotoğrafı bağlantısı
- Firebase tarafından üretilen kullanıcı kimliği (UID)

Bu bilgiler Cloud Firestore'daki **profilinde** saklanır. Dakk'a giriş yapmış diğer kullanıcılar bunları görebilir, böylece arkadaşların seni e-posta adresinle veya davet bağlantınla bulabilir. Görünen adını Ayarlar'dan değiştirebilirsin.

### 2. Şifreleme açık anahtarı
Cihazın bir anahtar çifti üretir:
- **Açık anahtar** profilinde yayınlanır. Başkalarının sana şifreli mesaj gönderebilmesi için gereklidir.
- **Özel anahtar** cihazından hiç çıkmaz ve Android Keystore ile korunur.

### 3. Mesajlar
- Mesaj içeriği ve sohbet listesinde görünen son mesaj önizlemesi **uçtan uca şifreli** olarak Firestore'da saklanır. Sunucuda yalnızca okunamayan şifreli metin bulunur.
- Mesajın iletilebilmesi için bazı bilgiler **şifrelenmeden** saklanır:
  - gönderen ve alıcı kimliği,
  - gönderim zamanı,
  - iletim ve okundu durumu,
  - gönderenin görünen adı,
  - mesaj türü (yazı veya fotoğraf).

### 4. Fotoğraflar
- Gönderdiğin fotoğraf önce telefonunda küçültülür, sonra **uçtan uca şifrelenir**.
- Şifreli hali parçalar halinde Firestore'a geçici olarak yüklenir.
- Alıcı fotoğrafı açtığında fotoğraf onun cihazına kaydedilir ve sunucudaki şifreli kopya **hemen silinir**.
- Hiç açılmayan fotoğraflar **7 gün** sonra sunucudan silinir.
- Fotoğraflar, gönderen ve alan cihazlarda uygulamanın kendi alanında saklanır. Galeriye yalnızca sen *Kaydet* dersen kopyalanır.

### 5. Kişi listesi
Eklediğin kişilerle ilgili şu bilgiler, yalnızca senin okuyabildiğin bir alanda saklanır:
- kimlik,
- ad,
- e-posta,
- fotoğraf bağlantısı,
- engelli olup olmadığı.

### 6. Bildirim jetonu
Uygulama kapalıyken yeni mesajları haber verebilmek için cihazına özel bir Firebase Cloud Messaging jetonu üretilir ve hesabınla birlikte saklanır. Bu jetonu kimse okuyamaz, yalnızca Dakk sunucusu kullanır. Çıkış yaptığında jeton silinir.

### 7. Cihazda tutulanlar
Şunlar yalnızca cihazında durur:
- ayarların,
- şifreli özel anahtarın,
- aldığın ve gönderdiğin fotoğraflar,
- Dakk Asistan sohbet geçmişin.

Uygulama yedeklemeye kapalıdır, bu veriler Google yedeğine aktarılmaz.

## Dakk Asistan (yapay zekâ)

Dakk Asistan isteğe bağlıdır ve ilk kullanımda seni uyarır.

- **Asistan sohbeti:** Asistana yazdığın mesajlar yanıt üretebilmesi için yapay zekâ hizmetine gönderilir. Asistanın konuşmayı hatırlayabilmesi için önceki mesajların da gönderilir. Geçmiş yalnızca cihazında saklanır ve *Sohbeti temizle* ile silebilirsin.
- **Sohbette @dakk:** Bir sohbette mesajına @dakk ile başladığında iki şey olur:
  - O sohbetin **son 30 mesajı** yanıt üretmek için yapay zekâ hizmetine gönderilir.
  - Asistanın yanıtı sohbete şifreli bir mesaj olarak eklenir ve karşı taraf da görür.

  Bu mesajlar, yapay zekâya gönderildikleri için uçtan uca şifreleme kapsamı dışında kalır. Karşı tarafın da bunu bilmesi için asistan yanıtları sohbette "Dakk Asistan" etiketiyle görünür.
- **Hizmet sağlayıcı:** Varsayılan olarak Google'ın **Gemini** modelleri **Firebase AI Logic** üzerinden kullanılır. Google'ın Gemini API koşulları ve gizlilik politikası geçerlidir. Ücretsiz katmanda Google, gönderilen içeriği hizmetlerini geliştirmek için kullanabilir. Dakk yöneticileri, OpenAI uyumlu başka bir yapay zekâ hizmeti de seçebilir. Bu durumda istekler Dakk sunucusu üzerinden o hizmete iletilir.
- Yapay zekâya kişisel, gizli veya hassas bilgi gönderme. Yapay zekâ yanıtları hatalı olabilir.

## İzinler

| İzin | Neden |
| --- | --- |
| İnternet / ağ durumu | Mesaj göndermek ve almak |
| Kamera | Yalnızca QR kod tararken. Görüntü kaydedilmez veya gönderilmez. |
| Fotoğraf seçici | Yalnızca gönderdiğin fotoğrafı seçerken. Galerinin geri kalanına erişilmez. |
| Depolama (yalnızca Android 9 ve öncesi) | Bir fotoğrafı galeriye kaydetmek istediğinde |
| Bildirimler | Yeni mesajları haber vermek |
| Uygulama yükleme | Uygulama içi güncellemeyi kurmak (yalnızca sen onaylarsan) |

## Üçüncü taraf hizmetler

- **Google Firebase** — hesap, şifreli mesajlar ve şifreli fotoğrafların geçici olarak saklanması, bildirim iletimi ve Dakk Asistan için kullanılır:
  - Authentication
  - Cloud Firestore
  - Cloud Messaging
  - AI Logic

  Google'ın gizlilik politikası: <https://policies.google.com/privacy>
- **Supabase** — Dakk sunucusu burada (Avrupa Birliği, Frankfurt) çalışır. Görevleri:
  - uygulama kapalıyken bildirim iletmek,
  - yöneticiler özel bir yapay zekâ hizmeti seçtiyse asistan isteklerini o hizmete aktarmak.

  Sunucu mesaj içeriğini okuyamaz, bildirimleri şifreli olarak iletir ve hiçbir veriyi kalıcı olarak saklamaz. Supabase'in gizlilik politikası: <https://supabase.com/privacy>
- **GitHub** — güncelleme denetimi için GitHub Releases'a anonim bir istek gönderilir ve APK buradan indirilir. GitHub'ın gizlilik politikası: <https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement>

## Verilerini silmek

- **Mesaj veya fotoğraf:** Gönderdiğin bir mesaja uzun basıp *Herkesten sil* dersen mesaj sunucudan kaldırılır.
- **Sohbet:** *Sohbeti temizle* mesajları yalnızca kendi cihazındaki görünümden kaldırır.
- **Asistan geçmişi:** Dakk Asistan ekranındaki *Sohbeti temizle* düğmesiyle silinir.
- **Hesap:** Hesabının ve tüm verilerinin silinmesini istiyorsan GitHub deposunda "Hesap silme talebi" başlıklı bir issue aç. E-posta adresini issue'ya **yazma**. Seninle iletişime geçilerek talebin işleme alınır.

## Çocuklar

Dakk 13 yaşından küçük çocuklara yönelik değildir.

## Değişiklikler

Bu politika güncellendiğinde değişiklikler bu dosyada ve [CHANGELOG.md](CHANGELOG.md)'de yer alır.
