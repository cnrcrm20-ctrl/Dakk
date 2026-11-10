# Katkıda Bulunma

Dakk'a katkı yapmak istediğin için teşekkürler! Başlamadan önce [README](README.md) içindeki mimari
bölümüne göz at. İmza, sürüm yayınlama veya güvenlik kurallarına dokunan değişiklikler için önce bir issue aç.

## Hata bildirmek / öneri sunmak

- Önce açık issue'lara bak, aynı konu varsa oraya yaz.
- Hata bildirirken: Dakk sürümü (Ayarlar → Hakkında), Android sürümü, telefon modeli,
  adımlar ve beklenen/gerçekleşen davranış.
- Güvenlik açıkları için issue açma; [SECURITY.md](SECURITY.md)'deki yolu izle.

## Geliştirme ortamı

- JDK 17, Android SDK (compileSdk 36), Android Studio (güncel sürüm)
- Derleme: `./gradlew assembleDebug`
- Testler: `./gradlew testDebugUnitTest`

## Kurallar

1. **İmza anahtarına dokunma.** `signing/dakk-release.p12.enc` (şifreli resmi anahtar) değişirse SHA-1
   değişir ve tüm kullanıcılarda Google ile giriş bozulur (hata 10). CI, imza değişirse derlemeyi durdurur.
   Fork'ta derlediğin APK debug anahtarıyla imzalanır; uygulama çalışır ama Google ile giriş hata verir.
2. **Yarım özellik ekleme.** Dakk normal kullanıcılar içindir; her özellik uçtan uca çalışır
   durumda olmalı. Çalışmayan buton veya "yakında" ekranı kabul edilmez.
3. **Firestore kuralları** (`firestore.rules`) ile uyumlu kal; kurallar değişirse aynı PR'da
   güncelle ve gerekçesini yaz.
4. **Mesaj içeriği asla şifresiz** Firestore'a yazılmaz.
5. Arayüz metinleri **Türkçe** ve son kullanıcının anlayacağı dilde olmalı.
6. Yeni mantık için `app/src/test` altında birim testi ekle.

## Pull request süreci

1. Kendi dalında çalış, küçük ve odaklı commit'ler at
   (ör. `fix: ...`, `feat: ...`, `docs: ...`, `ci: ...`).
2. Kullanıcıya görünen bir değişiklikse `CHANGELOG.md`'ye not ekle.
3. PR aç; GitHub Actions derlemesi ve imza kontrolü yeşil olmalı.
4. Sürüm numarasını yalnızca bakımcılar artırır. `main`'e birleşen yeni bir sürüm numarası,
   otomatik olarak GitHub Release oluşturur.

Katkıların [AGPL-3.0](LICENSE) lisansı altında yayınlanır.
