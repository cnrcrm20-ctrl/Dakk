# Dakk tanıtım videosu

30 saniye, dikey (1080×1920), yazı + müzik. Google uygulama reklamları tarzında: beyaz zemin, Google Sans,
ortada telefon içinde Dakk'ın sadeleştirilmiş, animasyonlu ekranları, sonda Dakk ördeği.
Ekran kaydı gerekmez; ekranlar `mock_screens.py`'de uygulamanın renk ve şekilleriyle kodla çizilir.

| Süre | Başlık | Telefonda |
| --- | --- | --- |
| 0–4 sn | Mesajların sadece **senin.** | — |
| 4–8 sn | Uçtan uca **şifreli.** | `1_sohbet` |
| 8–12 sn | Arkadaşını **saniyede** ekle. | `2_qr` |
| 12–16 sn | Maç akşamı? Takımları **Dakk** kursun. | `3_anket` |
| 16–20 sn | Her şey şifreli. Her şey **anında.** | `4_tepki` |
| 20–24 sn | Telefon değişir, sohbetler **kalır.** | `5_yedek` |
| 24–30 sn | Ördek animasyonu · Ücretsiz. Reklamsız. Açık kaynak. · **Hemen indir** | — |

## 69 saniyelik detaylı sürüm (`--uzun`)

9 sahne, her biri 6 sn, ardından "Hepsi tek uygulamada." özeti ve ördekli kapanış:
`dakk_tanitim_69sn.mp4` (+ `_sessiz`).

| Süre | Başlık | Telefonda |
| --- | --- | --- |
| 0–4 sn | Mesajların sadece **senin.** | — |
| 4–10 | Uçtan uca **şifreli.** | `1_sohbet` |
| 10–16 | Arkadaşını **saniyede** ekle. | `2_qr` |
| 16–22 | Ekibini tek yerde **topla.** | `6_grup`: kişiler seçilir, *Grubu kur*, grup sohbeti açılır |
| 22–28 | Maç akşamı? Takımları **Dakk** kursun. | `3_anket` |
| 28–34 | Tepkini bırak, **anında** gör. | `4_tepki` |
| 34–40 | Fotoğraflar da **korunur.** | `7_foto`: şifrelenerek yüklenir, açılınca sunucudan silinir, tam ekran yakınlaştırma |
| 40–46 | Yanında akıllı bir **asistan.** | `8_asistan`: grupta @dakk ile hatırlatma yazdırılır |
| 46–52 | Bazı mesajlar **iz** bırakmasın. | `9_kaybolan`: Sohbet bilgisi → 24 saat, "24 saat sonra…", mesajlar dağılır |
| 52–58 | Telefon değişir, sohbetler **kalır.** | `5_yedek` |
| 58–62 | Hepsi **tek** uygulamada. | 12 özellik çipi |
| 62–69 | Ördek · Ücretsiz. Reklamsız. Açık kaynak. · **Hemen indir** | — |

## "Dakk nasıl indirilir?" videosu (`--indir`)

30 sn, TikTok/Instagram için: `dakk_nasil_indirilir.mp4` (+ `_sessiz`). Ekranlar `mock_install.py`'de
(Chrome ve Android ekranları sade ve markasız çizilir).

| Süre | Başlık | Telefonda |
| --- | --- | --- |
| 0–4 sn | Dakk'ı **30 saniyede** kur. | — |
| 4–9 | ① Siteye gir, **İndir'e** dokun. | Adres yazılır, site açılır, *Android için indir* |
| 9–14 | ② Dosyayı **indir.** | Chrome uyarısı → *Yine de indir*, indirme çubuğu → *Aç* |
| 14–19 | ③ Yüklemeye **izin** ver. | *Ayarlar* → *Bu kaynaktan izin ver* anahtarı |
| 19–24 | ④ Yükle, aç. **Hazır!** | *Yükle* → *Aç* → Dakk ördeği → giriş ekranı |
| 24–30 | Ördek · site adresi · Android 7.0 ve üzeri | — |

APK adı ve boyutu `mock_install.py`'nin başında (`APK`, `APK_MB`); yeni sürümde güncelle.

## Telefondaki ekranlar (`mock_screens.py`)

| Sahne | Ne oluyor |
| --- | --- |
| `1_sohbet` | "Akşam halı saha var mı? ⚽" yazılıp gönderilir, tikler sarıya döner, "yazıyor" balonu, "Varım! 🔥" gelir. |
| `2_qr` | *Kişi ekle* → QR tarama, çizgi geçer, kod bulunur, alt sayfada *Kişilere ekle* → *Eklendi*. |
| `3_anket` | "Halı Saha" grubu, MAÇ ANKETİ'ne oylar akar → *Takım kur (6 kişi)* → "⚽ Takımlar hazır!". |
| `4_tepki` | Saha fotoğrafına uzun basılır, tepki çubuğundan ❤️ seçilir, "yazıyor", "Kaleci benim 🧤". |
| `5_yedek` | Yeni telefonda ana ekran: "Sohbet geçmişin geri geliyor…" dolar, sohbetler tek tek geri gelir. |

Renkler `ui/theme/Color.kt`, balonlar `ChatScreen.kt`, anket `PollComponents.kt`'den alınmıştır. Yazı ve isimleri
değiştirmek için ilgili `scene_*` fonksiyonunu düzenle; tek sahneye hızlı bakış:
`.venv/bin/python promo/mock_screens.py /tmp/ekranlar.png`.

İstersen herhangi bir sahneyi gerçek ekran kaydıyla değiştirebilirsin: `promo/clips/<sahne>.mp4` konursa o kullanılır.

## Video üretmek

```bash
python3 -m venv .venv && .venv/bin/pip install pillow numpy imageio-ffmpeg qrcode
.venv/bin/python promo/build_promo.py                    # promo/render/dakk_tanitim.mp4 (+ _sessiz)
.venv/bin/python promo/build_promo.py --frames 6,13.5    # yalnızca PNG önizleme
.venv/bin/python promo/build_promo.py --uzun             # 69 sn sürüm: promo/render/dakk_tanitim_69sn.mp4
.venv/bin/python promo/build_promo.py --indir            # nasıl indirilir: promo/render/dakk_nasil_indirilir.mp4
```

(İsteğe bağlı) gerçek klip ince ayarı `promo/clips/ayar.json`:

```json
{ "1_sohbet": { "start": 1.5, "speed": 1.25 } }
```

- `dakk_tanitim.mp4`: telifsiz, kodla üretilmiş müzikle (120 BPM, sahne geçişleri ölçü başına denk gelir).
- `dakk_tanitim_sessiz.mp4`: Instagram/TikTok'ta uygulamanın kendi müzik kütüphanesinden şarkı eklemek için.

Yazı tipi: Google Sans (SIL Open Font License, `assets/fonts/OFL.txt`); yoksa betik GitHub'dan indirir.
Emojiler: Noto Emoji (Apache 2.0, `assets/emoji/KAYNAK.txt`). `assets/halisaha.jpg`: yapay zekâ ile üretilmiş görsel.
