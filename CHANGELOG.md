# Değişiklik Günlüğü

Bu dosyadaki her `## [sürüm]` başlığı, GitHub Release notlarına otomatik olarak aktarılır.

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
