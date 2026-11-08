# Değişiklik Günlüğü

Bu dosyadaki her `## [sürüm]` başlığı, GitHub Release notlarına otomatik olarak aktarılır.

## [2.1.0] - 2026-11-08

### Yeni
- Açılış animasyonu: Dakk ördeği artık her açılışta seni karşılıyor. Ekrana dokunarak geçebilirsin.
- Güncelleme penceresi yenilendi: yalnızca yenilikler, başlıklar ve maddeler halinde düzgün görünüyor.
- Web sitesinde gizlilik politikası sayfası.

### Güvenlik
- Uygulama yeni, gizli tutulan bir imza anahtarıyla imzalanıyor. Eski anahtar herkese açık olduğu için
  emekliye ayrıldı; artık kimse resmi Dakk imzasıyla sahte bir sürüm üretemez.
- Uygulama içi güncelleyici, indirdiği APK'nın imzasını kurmadan önce kontrol ediyor. İmza farklıysa
  ne yapılacağını adım adım anlatıyor.

### Önemli
- 2.0.x sürümünden geçiş: imza anahtarı değiştiği için **eski sürümü bir kez silip** bu sürümü kurman
  gerekiyor. Kişilerin hesabında saklı kalır; şifreleme anahtarları cihazda yenilendiği için eski
  mesajlar yeni kurulumda okunamaz.

## [2.0.1] - 2026-11-08

### Düzeltildi
- Google hesabı seçildikten sonra giriş ekranına sessizce geri dönme sorunu. Google'ın yapılandırma
  hatalarını "iptal" olarak bildirdiği durumlar (`[16] Account reauth failed`) artık yakalanıyor;
  ekranda anlaşılır bir hata ve kopyalanabilir teknik ayrıntılar (sürüm, paket, imza SHA-1) gösteriliyor.
- İlk giriş yöntemi başarısız olursa Dakk, alternatif Google giriş penceresiyle otomatik olarak
  bir kez daha deniyor (bazı Xiaomi/MIUI cihazlarda ilk yöntem hesap seçiminden sonra kapanabiliyor).

## [2.0.0] - 2026-11-08

Dakk baştan yeniden yazıldı. Bu sürüm temiz kurulum gerektirir.

### Düzeltildi
- **Google ile giriş hatası** (`10: [28444] Developer console is not set up correctly`) giderildi.
  Uygulama artık her sürümde aynı, sabit imza anahtarıyla imzalanıyor; SHA-1 değişmiyor.
- Güncellemeler artık mevcut kurulumun üzerine sorunsuz yükleniyor (imza her sürümde aynı).

### Yeni
- Gerçek, güvenilir mesajlaşma: mesajlar karşı taraf çevrimdışıyken bile iletilir.
- Uçtan uca şifreleme (P-256 ECDH + AES-256-GCM); özel anahtar Android Keystore ile korunur.
- Okundu bilgisi (✓ gönderildi, ✓✓ okundu) ve gönderiliyor göstergesi.
- Kişi ekleme: QR kod tarama, galeriden QR okuma, kendi QR kodunu gösterme, davet bağlantısı, e-posta ile arama.
- Güvenlik numarası ile sohbet doğrulama.
- Mesaj kopyalama ve "herkesten sil".
- Kişi engelleme / engel kaldırma, sohbeti bu cihazdan temizleme.
- Görünen adı değiştirme.
- Yeni mesaj bildirimleri; bildirime dokununca ilgili sohbet açılır.
- Uygulama içi otomatik güncelleme (GitHub Releases) ve açma/kapama ayarı.
- Giriş hatalarında anlaşılır Türkçe mesajlar ve teknik ayrıntılar.

### Kaldırıldı
- Çalışmayan veya yarım kalan özellikler: sesli/görüntülü arama, fotoğraf/belge/konum gönderimi,
  sesli mesaj, grup sohbeti, ağ teşhis laboratuvarı, yapay zekâ asistanı, PIN ile eşleşme.
- Gereksiz izinler: mikrofon ve ses ayarları.

### Önemli
- 1.x sürümlerinden geçiş: imza anahtarı değiştiği için **eski sürümü silip** bu sürümü kurun.
  Bundan sonraki tüm güncellemeler doğrudan uygulama içinden yüklenecektir.

## [1.0.11] - 2026-11-08
- Son 1.x sürümü (rastgele imza anahtarıyla derlendi; Google girişi çalışmıyordu).
