# Dakk P2P Messenger - Sistem Hafızası (Brain)

## Proje Tanımı
Dakk, %100 doğrudan cihazdan cihaza (P2P) çalışan, merkezi sunucu ve veri tabanı barındırmayan (0 TL maliyetli), uçtan uca şifreli (E2EE) ve Dakk maskotu (ördek 🐥) mizahına sahip modern bir Android mesajlaşma uygulamasıdır.

- **Paket Adı:** `com.aistudio.dakk.p2pchat`
- **Mevcut Sürüm:** `1.0.10` (VersionCode: 10)
- **GitHub Repo:** `https://github.com/cnrcrm20-ctrl/Dakk.git`
- **GitHub Pages:** `https://cnrcrm20-ctrl.github.io/Dakk/`

---

## Temel Bileşenler & Mimari

1. **P2P WebRTC & Şifreleme (`WebRtcManager.kt`, `CryptoEngine.kt`):**
   - STUN / TURN röleleri üzerinden doğrudan P2P DataChannel bağlantısı kurulur.
   - Her kullanıcının 512 karakterlik benzersiz ECDSA/AES anahtarları cihazında yerel olarak üretilir.

2. **Otomatik Çift Yönlü El Sıkışma & Rendezvous (`SignalingPayload.kt`, `ZeroCostRendezvous.kt`):**
   - Teklif (`OFFER`) oluşturulduğunda benzersiz bir `roomId` atanır.
   - Karşı taraf daveti kabul ettiğinde cevabı (`ANSWER`) bu odaya aktarır ve iki taraf da manuel işlem yapmadan anında eşleşir.

3. **QR Kod İle Arkadaş Ekleme (`CameraQrScannerView.kt`, `QrCodeHelper.kt`):**
   - Kameradan canlı yüksek çözünürlüklü CameraX tarama ile anında QR algılama.
   - Galeriden seçilen QR görsellerinde gelişmiş ZXing binarization ile çözümleme.

4. **Davet Bağlantıları & GitHub Pages (`index.html`, `404.html`, `invite/index.html`, `docs/`):**
   - WhatsApp paylaşımında otomatik olarak kişisel P2P davet linki (`https://cnrcrm20-ctrl.github.io/Dakk/invite?data=...`) üretilir.
   - GitHub Pages alt yollarındaki 404 hataları önlenmiş, otomatik `dakk://invite?data=...` yönlendirmesi ve APK indirme butonu eklenmiştir.

5. **6 Haneli Hızlı PIN İle Eşleşme (`ZeroCostRendezvous.kt`):**
   - Yan yana veya uzakta olan iki kullanıcı aynı 6 haneli kodu girdiğinde açık sinyalleşme odasında teklif ve cevaplar otomatik takas edilir.

6. **Zengin Bildirimler (`NotificationHelper.kt`):**
   - Gelen mesajlar, sesli/görüntülü aramalar ve arkadaşlık bağlantıları için yüksek öncelikli bildirim kanalları.

---

## Son Düzeltmeler & Güncellemeler (Sürüm 1.0.10)
- Live CameraX QR Tarayıcı (`CameraQrScannerView.kt`) eklendi; düşük çözünürlüklü thumbnail hatası ve "QR kod bulunamadı" sorunu tamamen çözüldü.
- `SignalingPayload.decodeFromCompactString` esnekleştirildi; yapıştırılan WhatsApp mesajlarından veya URL'lerden `data=` içeriği otomatik ayrıştırılıyor.
- 6 Haneli PIN oda mantığı çift yönlü (Offer/Answer) yayın yapacak şekilde düzeltildi.
- En dış katmana sistem hafıza dosyası `/brain.md` eklendi.
