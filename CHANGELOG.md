# Değişiklik Günlüğü

Bu dosyadaki her `## [sürüm]` başlığı, GitHub Release notlarına otomatik olarak aktarılır.

## [2.6.0] - Geliştiriliyor

### Yeni
- **Sesli mesajlar 🎤:** Yazı alanı boşken gönder düğmesi mikrofona dönüşüyor; dokun, konuş, gönder. En fazla
  5 dakika. Kayıtlar fotoğraflar gibi uçtan uca şifreleniyor. Dinlerken dalga formuna dokunarak ileri/geri
  sarılabiliyor, 1× / 1,5× / 2× hız seçilebiliyor. Sohbetteki herkesin 2.6.0 kullanması gerekiyor.
- **Yıldızlı mesajlar ⭐:** Bir mesaja basılı tut → *Yıldızla*. Sohbet bilgilerindeki *Yıldızlı* düğmesinden
  hepsini gör, dokununca mesaja git. Yıldızlar yalnızca senin telefonunda tutuluyor.
- **Mesaj sabitleme 📌:** Önemli bir mesajı sohbetin üstüne sabitle (basılı tut → *Sabitle*). Sabitlenen mesaj
  herkesin sohbetinin üstünde görünüyor; dokununca mesaja gidiyor. İçerik yine şifreli kalıyor.
- **Yılbaşı 🎄 (15 Aralık – 1 Ocak):** Noel Baba şapkalı ördek, sohbet listesinde kar ve yeni yıla geri sayım,
  sohbette gönderilebilen 4 yılbaşı kartı, "mutlu yıllar" yazınca konfeti ve gece yarısı kutlaması.
  Ayarlar'dan kapatılabiliyor; 2 Ocak'ta kendiliğinden kalkıyor.

### İyileştirildi
- **Özellikler uzaktan açılabiliyor:** Yeni özellikler, uygulamayı yeniden indirmeye gerek kalmadan sunucudan
  açılıp kapatılabiliyor. Bir sorun çıkarsa özellik herkes için anında kapatılabiliyor.

## [2.5.0] - 2026-11-15

### Yeni
- **Kurulum sihirbazı:** İlk açılışta Dakk adım adım tanıtılıyor: Google ile giriş, sohbet geçmişini geri
  alma, profil, bildirim izni, sohbet yedeği, güncelleme izni ve pil ayarı. Verilmiş izinlerin adımları
  atlanıyor. Mevcut kullanıcılar yalnızca yeni adımları (ör. sohbet yedeği) bir kez görüyor.
- **Kısa tanıtım turu:** Yeni kullanıcılara ana ekranda yaklaşık 30 saniyelik bir tur: kişi ekleme, yeni
  sohbet, grup kurma ve ayarlar.
- **Sohbet yedeği (arkadaşlardan geri yükleme):** Ayarlar → *Sohbet yedeği* (varsayılan açık). Telefonu sıfırlarsan veya
  Dakk'ı yeniden kurarsan, yedeği açık olan arkadaşlarının telefonları seninle olan sohbet geçmişini
  yeni anahtarına şifreleyip geri gönderiyor (en fazla 14 gün içinde). Sunucu içeriği göremez.
- **İzinler ekranı:** Ayarlar → *İzinler*: bildirim, kamera, güncelleme yükleme ve pil izinlerinin durumu
  tek yerde; Xiaomi/Redmi/POCO için otomatik başlatma rehberi.

### İyileştirildi
- **Sunucu artık posta kutusu:** Herkes 2.5.0 kullandığında, teslim edilip okunan mesajlar telefonlarda
  saklanıp sunucudan kaldırılıyor (birebir sohbette okunduktan sonra, gruplarda 30 gün sonra). Anketler
  sunucuda kalıyor. Sohbetler daha hızlı açılıyor, sunucu kotası çok daha geç doluyor.
- **Sıkıştırılmış mesajlar:** Mesajlar şifrelenmeden önce Türkçeye göre ayarlanmış bir sözlükle
  sıkıştırılıyor; kısa mesajlar yaklaşık üçte bir daha küçük. (Sohbetteki herkes 2.5.0 kullanıyorsa.)
- **Daha küçük fotoğraflar:** Fotoğraflar mümkünse WebP olarak aktarılıyor; telefonda yine JPEG
  olarak kaydediliyor.
- **Daha kolay güncelleme:** Android 12 ve üzerinde güncelleme çoğu zaman ek onay istemeden kuruluyor;
  bittiğinde "Dakk güncellendi ✓" bildirimi geliyor. Kurulum başarısız olursa klasik yükleyiciye geçiliyor.
- **Anahtar değişince yeniden gönderim:** Arkadaşın Dakk'ı yeniden kurduysa, ona gönderdiğin son mesajlar
  yeni anahtarıyla yeniden şifreleniyor; okunamayan mesaj kalmıyor.
- **Fotoğraf deposu yedekli:** Fotoğraflar iki depoya dağıtılıyor; biri dolarsa veya yanıt vermezse diğeri
  kullanılıyor.
- Sistem notlarında Türkçe ekler artık doğru ("Ali'ye", "Can'ın", "Ayşe'ye" …).

## [2.4.1] - 2026-11-15

### İyileştirildi
- **Kurucu paneli:** İstatistik bölümünde giriş yapan hesapların listesi (ad, Google hesabı ve uygulamayı
  son açtığı zaman) görünüyor. Yalnızca kurucular görür; diğer kullanıcılar için değişiklik yok.

## [2.4.0] - 2026-11-15

### Yeni
- **Veri tasarrufu:** Ayarlar → *Veri tasarrufu* → Normal / Tasarruf / Otomatik (varsayılan Otomatik).
  Tasarrufta fotoğraflar 960 piksel gönderilir ve grup fotoğrafları dokununca iner. Otomatik, sunucunun
  aylık kotası dolmaya yaklaşınca kendiliğinden tasarrufa geçer; kota iyice daralırsa "yazıyor…" da
  geçici olarak kapanır. Mesajlaşma hiçbir zaman durmaz.
- **Telefonda mesaj arşivi:** Mesajlar telefonda da (şifreli haliyle, sıkıştırılarak) saklanıyor. Sohbet
  açılınca mesajlar internet beklemeden anında geliyor; sunucudan yalnızca son 150 mesaj dinleniyor.
  İlk açılışta her sohbetin geçmişi bir kez telefona aktarılıyor.

### İyileştirildi
- **Fotoğraflar:** Artık ayrı, şifreli bir fotoğraf deposuna gidiyor. Fotoğraflar 1280 piksel JPEG
  (genelde 60–150 KB) olarak gönderiliyor, eskisinden çok daha hızlı yükleniyor ve iniyor. Birebir
  sohbette karşı taraf indirince sunucudan siliniyor; gruplarda ve açılmayanlarda 7 gün sonra siliniyor.
  Kayıtlı fotoğraflar her zaman JPEG.
- **Daha hızlı bildirimler:** Kısa grup mesajları da artık bildirimin içinde (şifreli) geliyor; telefon
  mesajı ayrıca indirmek zorunda kalmıyor.
- **Daha az sunucu yükü:** Bildirim jetonları Firestore yerine Dakk sunucusunda tutuluyor. Engellenen
  kişilerden gelen bildirimler telefonda sessizce düşürülüyor.

### Düzeltildi
- Ankette art arda hızlı dokununca oyların kaybolması.
- *Takım kur*'da aynı adı taşıyan iki oyuncudan birinin listeden düşmesi.
- Gruptan ayrılan kişilerin oylarının sayılmaya devam etmesi.
- *Takım kur*'a ikinci kez basılınca takımların sorulmadan yeniden yazılması (artık "Yeniden karıştırılsın
  mı?" diye soruluyor).
- Gruba sonradan katılan kişinin eski anketlerde "çözülemedi" görmesi (artık "Bu anket sen gruba
  katılmadan önce açıldı" yazıyor).
- Anket seçeneklerinin bazı durumlarda kayarak yanlış seçeneğe oy sayılabilmesi.

### Önemli
- 2.4.0 ile gönderilen fotoğrafları 2.3.0 kullananlar açamaz. Herkesin güncellemesi önerilir.

## [2.3.0] - 2026-11-14

### Yeni
- **Gruplar:** En fazla 50 kişilik gruplar kurabilirsin. Sohbetler sekmesindeki 👥+ simgesi → kişileri seç →
  grup adı ve fotoğrafı. Grup mesajları da uçtan uca şifreli. Tepkiler, yanıtlar, fotoğraflar ve ekran
  görüntüsü bilgisi gruplarda da çalışıyor. Bildirim başlığında "Grup · Gönderen" yazıyor.
  - Grubu kuran kişi yöneticidir. Yöneticiler kişi ekleyip çıkarabilir, başkalarını yönetici yapabilir,
    grubun adını ve fotoğrafını değiştirebilir.
  - **Davet bağlantısı:** Yönetici açarsa bağlantıya dokunan kişi gruba katılabilir. Bağlantı istenince
    yenilenebilir; eski bağlantı çalışmaz hale gelir.
  - Herkes istediği zaman gruptan ayrılabilir.
- **Anketler:** Grupta 📊 simgesiyle 2–10 seçenekli anket aç; tek veya çoklu seçim. Kim neye oy verdi
  görünür, oyunu istediğin zaman değiştirebilirsin. Sorular ve oylar da şifreli.
- **⚽ Maç anketi + Takım kur:** Hazır "Maça geliyor musun?" anketi. *Takım kur* düğmesi "Geliyorum"
  diyenleri rastgele iki dengeli takıma bölüp sonucu gruba yazıyor.
- **Yazıyor balonu:** Karşındaki yazarken sohbette zıplayan sarı noktalı bir balon çıkıyor; başlıkta ve
  sohbet listesinde "yazıyor…" görünüyor. Gruplarda kimin yazdığı da görünüyor ("ronaldoo7 ve 2 kişi
  yazıyor…"). Sinyal hiçbir yerde saklanmıyor, yazmayı bırakınca birkaç saniyede kayboluyor.
- **Sohbet bilgi ekranı:** Sohbetin başlığına dokun. Profil, sohbette arama, sessize alma, kaybolan mesajlar,
  takma adlar, gizlilik ve güvenlik, paylaşılan fotoğraflar ve bağlantılar tek ekranda. Grupta kişiler,
  yöneticiler ve davet bağlantısı da burada.
- **Sessize al:** 8 saat, 1 hafta veya her zaman. Sessizdeki sohbet bildirim göndermez, listede 🔕 ile görünür.
- **Takma adlar:** Sohbetteki herkesin gördüğü takma adlar. Birebir sohbette iki taraf da birbirine takma ad
  verebilir. Grupta herkes kendi takma adını, yöneticiler herkesinkini değiştirebilir. Değişiklik sohbete
  küçük bir notla yazılıyor.
- **Kişiye özel fotoğraf:** Birebir sohbette karşındakine yalnızca senin telefonunda görünen bir fotoğraf
  verebilirsin.
- **Kaybolan mesajlar:** Sohbet başına Kapalı / 24 saat / 7 gün. Açıkken yeni mesajlar süre dolunca
  herkesten siliniyor.

### İyileştirildi
- **Yeni güncelleme ekranı:** Tam ekran, sarı ışıltılı ve modern bir tasarım. Sürüm geçişi, boyut,
  "Neler yeni?" kartı ve dolan bir indirme düğmesi var. "Güncelleme gerekli" ekranı da aynı tasarıma geçti.
- Ekranlar arası geçişler daha akıcı.

### Önemli
- Gruplar, anketler ve yazıyor balonu için **iki tarafın da 2.3.0 kullanması** gerekiyor.
- Gizlilik politikası güncellendi: gruplar, takma adlar ve yazıyor sinyaliyle ilgili bölümler eklendi.

## [2.2.0] - 2026-11-11

### Yeni
- **Uygulama kapalıyken bildirim:** Artık Dakk tamamen kapalıyken de yeni mesaj bildirimi geliyor.
  Bildirimde gönderenin adı ve mesaj görünüyor; mesaj yine yalnızca senin telefonunda çözülüyor.
- **Tepkiler:** Bir mesaja uzun basınca 😂 ❤️ ⭐ 👌 tepkilerinden birini bırakabilirsin. Tepkiler baloncuğun
  altında görünüyor; aynı tepkiye yeniden dokunursan kaldırılıyor. Tepkiler de uçtan uca şifreli.
- **Yanıtla:** Mesaja uzun bas → *Yanıtla*. Yanıtın, alıntılanan mesajla birlikte görünüyor; alıntıya
  dokununca o mesaja gidiliyor.
- **Bildirimden hızlı işlemler:** Bildirimde üç düğme var: *Cevapla* (uygulamayı açmadan yanıt yaz),
  *Görüldü* (okundu yap) ve *❤️* (son mesaja kalp bırak).
- **Ekran görüntüsü bilgisi:** Bir sohbette ekran görüntüsü alındığında karşı tarafın sohbetinde
  "… ekran görüntüsü aldı" yazısı çıkıyor. Ekran görüntüsü engellenmiyor. (Android 14 ve üzeri)
- **Sohbet arama:** Sohbetler listesinin üstünde yeni bir arama çubuğu var. Kişi adında ve son mesajda
  arıyor, eşleşen kısmı sarıyla vurguluyor, henüz yazışmadığın kişileri de gösteriyor. Türkçe karakterleri
  yazmasan da buluyor ("cagri" yazınca "Çağrı" çıkıyor).
- **Kilit ekranında gizlilik:** Telefonunda "hassas içeriği gizle" açıksa kilit ekranında yalnızca
  "Yeni mesaj" yazıyor; mesaj içeriği telefonun kilidi açılınca görünüyor.

### İyileştirildi
- Yeni mesaj gelen veya yazdığın sohbet artık her zaman listenin en üstünde görünüyor. Önceden liste
  yerinde kaldığı için en üste çıkan sohbet ekranın dışında kalabiliyordu.
- Bildirim sunucusuna ulaşılamadığında Dakk, uygulama arka plandayken bildirim göstermeye devam ediyor.
- Gizlilik politikası ve güvenlik sayfası sadeleştirildi.

## [2.1.0] - 2026-11-08

### Yeni
- **Dakk Asistan:** Uygulamanın içinde bir yapay zekâ asistanı. Sohbet listesinin en üstünde duruyor; soru sorabilir, mesaj yazdırabilir, fikir alabilirsin. Konuşmayı hatırlıyor; geçmiş yalnızca telefonunda saklanıyor.
- **Sohbette @dakk:** Bir sohbette mesajına @dakk ile başlarsan asistan sohbetin son mesajlarını okuyup yanıtını sohbete ekliyor. Yanıtı karşı taraf da görüyor ve "Dakk Asistan" etiketiyle ayırt ediliyor. İlk kullanımda yapay zekâ uyarısı çıkıyor.
- **Fotoğraf gönderme:** Sohbetlerde fotoğraf gönderebilirsin. Fotoğraflar telefonunda küçültülüp uçtan uca şifreleniyor. Karşı taraf fotoğrafı açtığında sunucudaki şifreli kopya siliniyor. Fotoğrafa dokununca tam ekran açılıyor; iki parmakla yakınlaştırabilir ve galeriye kaydedebilirsin.
- **Uygulama kapalıyken bildirim:** Dakk sunucusu kuruluysa uygulama kapalıyken de yeni mesaj bildirimi geliyor. Bildirimde gönderenin adı ve mesaj görünüyor, fotoğraflar için "📷 Fotoğraf" yazıyor. Mesaj telefonunda çözülüyor; sunucu içeriği göremiyor.
- **Duyurular:** Önemli haberler açılışta tam ekran bir duyuru olarak gösteriliyor. Her duyuru bir kez çıkıyor.
- **Açılış animasyonu:** Dakk ördeği her açılışta seni karşılıyor. Geçmek için ekrana dokunabilirsin.
- **Güncelleme penceresi yenilendi:** Artık yalnızca yenilikler, başlıklar ve maddeler halinde düzgün görünüyor.
- Desteği biten sürümler için "Güncelleme gerekli" ekranı, kısa bakım çalışmaları için bakım ekranı.
- Web sitesinde gizlilik politikası sayfası.

### Güvenlik
- Uygulama yeni, gizli tutulan bir imza anahtarıyla imzalanıyor. Eski anahtar herkese açık olduğu için emekliye ayrıldı. Artık kimse resmi Dakk imzasıyla sahte bir sürüm üretemez.
- Uygulama içi güncelleyici, indirdiği APK'nın imzasını kurmadan önce kontrol ediyor. İmza farklıysa ne yapılacağını adım adım anlatıyor.
- Uygulama ayarları yalnızca kurucu hesapları tarafından değiştirilebiliyor. Bu yetki uygulamanın içinde değil, sunucu kurallarında tanımlı.

### Önemli
- 2.0.x sürümünden geçiş: İmza anahtarı değiştiği için **eski sürümü bir kez silip** bu sürümü kurman gerekiyor. Kişilerin hesabında saklı kalır. Şifreleme anahtarları cihazda yenilendiği için eski mesajlar yeni kurulumda okunamaz.
- Gizlilik politikası güncellendi: Dakk Asistan, fotoğraflar ve bildirimlerle ilgili bölümler eklendi.

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
