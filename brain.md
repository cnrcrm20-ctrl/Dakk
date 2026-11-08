# Dakk Messenger - Sistem Hafızası (Brain)

## Proje Tanımı
Dakk, doğrudan cihazdan cihaza (P2P WebRTC) ve ücretsiz Firebase Firestore bulut altyapısını hibrit birleştiren, Google ile güvenli oturum açan, uçtan uca şifreli (E2EE) ve Dakk maskotu (ördek 🐥) mizahına sahip modern bir Android mesajlaşma uygulamasıdır.

- **Paket Adı:** `com.aistudio.dakk.p2pchat`
- **Mevcut Sürüm:** `1.0.11` (VersionCode: 11)
- **GitHub Repo:** `https://github.com/cnrcrm20-ctrl/Dakk.git`
- **GitHub Pages:** `https://cnrcrm20-ctrl.github.io/Dakk/`
- **Firebase Proje ID:** `gen-lang-client-0161098568`
- **Firestore Veritabanı ID:** `ai-studio-android-e2d067e8-8aba-4fce-a486-b836129f76c1`

---

## Temel Bileşenler & Hibrit Mimari

1. **Google ile Oturum Açma (`AuthScreen.kt`, Jetpack Credential Manager):**
   - Jetpack `CredentialManager` ve `GetSignInWithGoogleOption` ile tek tıkla güvenli Google girişi.
   - Oturum kapalıyken uygulama `AuthScreen` kapısıyla korunur; oturum açıldığında kullanıcının profili Firebase Firestore'da `/users/{uid}` altına eşitlenir.

2. **Firebase Firestore Veritabanı & Kişi Senkronizasyonu (`FirestoreRepository.kt`, `FirestoreModels.kt`):**
   - Kullanıcı profilleri, kişi listesi (`/users/{uid}/contacts`) ve mesajlar Firestore üzerinde anlık `snapshots()` akışlarıyla senkronize edilir.
   - P2P el sıkışmaları ve QR kod sinyalleşmeleri için `/rendezvous/{roomId}` odası kullanılır; böylece simetrik NAT veya internet kısıtlamalarında bağlantı kopmaz.

3. **P2P WebRTC & Uçtan Uca Şifreleme (`WebRtcManager.kt`, `CryptoEngine.kt`):**
   - Eşler doğrudan bağlıyken STUN/TURN üzerinden düşük gecikmeli P2P DataChannel iletişimi devam eder.
   - Her mesaj cihaz üzerinde yerel ECDSA / AES-256-GCM ile zırhlanır.

4. **Gelişmiş QR Kod & Arkadaş Ekleme (`CameraQrScannerView.kt`, `QrCodeHelper.kt`):**
   - CameraX ile gerçek zamanlı yüksek çözünürlüklü tarama.
   - QR kodlar ve davet linkleri artık kullanıcının doğrulanmış Dakk & Google kimliğini anında tanır.

5. **Güvenlik Kuralları (`firestore.rules`):**
   - Çok kullanıcılı sıfır güven (zero-trust) mimarisi.
   - JavaScript kurallar testleri (`firestore.test.js`) ve Robolectric yerel JVM entegrasyon testleri (`FirestoreRepositoryRuleTest.kt`) ile %100 doğrulanıp canlıya dağıtılmıştır (`DeployRules`).

6. **Zengin Bildirimler (`NotificationHelper.kt`):**
   - Gelen mesajlar ve arkadaşlık eşleşmeleri için yüksek öncelikli bildirim kanalları.
