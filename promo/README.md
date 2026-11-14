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
```

(İsteğe bağlı) gerçek klip ince ayarı `promo/clips/ayar.json`:

```json
{ "1_sohbet": { "start": 1.5, "speed": 1.25 } }
```

- `dakk_tanitim.mp4`: telifsiz, kodla üretilmiş müzikle (120 BPM, sahne geçişleri ölçü başına denk gelir).
- `dakk_tanitim_sessiz.mp4`: Instagram/TikTok'ta uygulamanın kendi müzik kütüphanesinden şarkı eklemek için.

Yazı tipi: Google Sans (SIL Open Font License, `assets/fonts/OFL.txt`); yoksa betik GitHub'dan indirir.
Emojiler: Noto Emoji (Apache 2.0, `assets/emoji/KAYNAK.txt`). `assets/halisaha.jpg`: yapay zekâ ile üretilmiş görsel.
