# Dakk sunucusu (Cloudflare Worker)

Dakk'ın tek sunucu parçası. **Ücretsizdir.** Cloudflare'in ücretsiz planı günde 100.000 isteğe kadar izin verir ve kart bilgisi istemez.

Ne işe yarar:

| Uç nokta | Görev |
|---|---|
| `POST /notify` | Uygulama kapalıyken gelen mesaj bildirimi (Firebase Cloud Messaging) |
| `POST /assistant` | Kurucu panelinde **Özel API** seçildiyse OpenAI uyumlu uç noktaya vekillik eder. Anahtar uygulamalara hiç gitmez. |

Sunucu mesajları **okuyamaz**. Bildirim içeriği şifreli olarak iletilir ve alıcının telefonunda çözülür. Sunucu yalnızca şunları denetler:

- isteği yapan kişinin o sohbetin üyesi olduğunu,
- mesajı gerçekten onun gönderdiğini,
- alıcının onu engellemediğini.

Sunucu kurulmazsa Dakk yine çalışır. Bu durumda bildirimler yalnızca uygulama açıkken veya arka planda çalışırken gelir.

---

## Kurulum (yaklaşık 10 dakika, tek seferlik)

### 1. Firebase hizmet hesabı anahtarı

1. [Firebase Console](https://console.firebase.google.com) › Dakk projesi › ⚙️ **Proje ayarları** › **Hizmet hesapları** sekmesine gidin.
2. **Yeni özel anahtar oluştur** › **Anahtar oluştur** deyin. Bir `.json` dosyası iner.
3. Bu dosya projenize tam erişim verir. **Kimseyle paylaşmayın ve GitHub'a yüklemeyin.**

Aynı sayfada **Cloud Messaging** sekmesinde *Firebase Cloud Messaging API (V1)* "Etkin" görünmelidir. Görünmüyorsa yanındaki menüden etkinleştirin.

### 2. Worker'ı oluşturun

1. [dash.cloudflare.com](https://dash.cloudflare.com) adresinde ücretsiz hesap açın veya giriş yapın.
2. **Workers & Pages** › **Create** › **Create Worker** yolunu izleyin.
3. Ad olarak örneğin `dakk-push` yazıp **Deploy** deyin.
4. **Edit code** deyin, editördeki her şeyi silin, bu klasördeki [`dakk-worker.js`](dakk-worker.js) dosyasının tamamını yapıştırın ve **Deploy** deyin.

### 3. Gizli anahtarı ekleyin

1. Worker sayfasında **Settings** › **Variables and Secrets** › **Add** yolunu izleyin.
2. Alanları şöyle doldurun:
   - Type: **Secret**
   - Variable name: `FIREBASE_SERVICE_ACCOUNT`
   - Value: 1. adımda inen JSON dosyasının **tüm içeriği** (Not Defteri ile açıp kopyalayın)
3. **Deploy** deyin.

### 4. Adresi Dakk'a girin

1. Worker'ın adresini kopyalayın. Örnek: `https://dakk-push.kullaniciadi.workers.dev`
2. Tarayıcıda açtığınızda `{"ok":true,"service":"dakk",...}` görmelisiniz.
3. Dakk'ta yolu izleyin: **Ayarlar** › **Kurucu paneli** › **Dakk sunucusu** › **Sunucu adresi**.
4. Adresi yapıştırıp **Kaydet** deyin.

Bundan sonra herkes uygulamayı bir kez açıp kapattığında bildirim jetonu kaydolur. Kapalı uygulamaya bildirim gelmeye başlar.

---

## Sorun giderme

- **Xiaomi / Redmi / POCO (MIUI, HyperOS):** Ayarlar › Uygulamalar › Dakk › **Otomatik başlatma** açık olmalıdır. Pil tasarrufu da **Kısıtlama yok** olmalıdır. Aksi halde sistem, kapalı uygulamaya bildirim ulaşmasını engeller. Huawei, Oppo ve Vivo'da da benzer ayarlar vardır.
- **Hiç bildirim gelmiyor:** Cloudflare › Worker › **Logs** bölümünde hata olup olmadığına bakın. `Erişim jetonu alınamadı` hatası, gizli anahtarın eksik veya yanlış yapıştırıldığını gösterir.
- **Veritabanı farklıysa:** Firestore veritabanı kimliği varsayılan değerden farklıysa `FIRESTORE_DATABASE_ID` adlı bir **Text** değişkeni ekleyin.

## Güvenlik notları

- Hizmet hesabı anahtarı yalnızca Cloudflare'in şifreli gizli değişkenlerinde durur.
- Anahtarın sızdığından şüphelenirseniz Firebase Console › Hizmet hesapları sayfasından eski anahtarı silin ve yenisini oluşturun.
- Uç noktalar Firebase oturumu olmadan çağrılamaz. Bir kullanıcı yalnızca kendi gönderdiği mesajlar için bildirim tetikleyebilir.
