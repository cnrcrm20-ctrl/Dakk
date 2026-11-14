# Dakk tanıtım videosu

30 saniye, dikey (1080×1920), yazı + müzik. Google uygulama reklamları tarzında: beyaz zemin, Google Sans,
ortada telefon içinde gerçek ekran kayıtları, sonda Dakk ördeği.

| Süre | Başlık | Telefonda |
| --- | --- | --- |
| 0–4 sn | Mesajların sadece **senin.** | — |
| 4–8 sn | Uçtan uca **şifreli.** | `1_sohbet` |
| 8–12 sn | Arkadaşını **saniyede** ekle. | `2_qr` |
| 12–16 sn | Maç akşamı? Takımları **Dakk** kursun. | `3_anket` |
| 16–20 sn | Her şey şifreli. Her şey **anında.** | `4_tepki` |
| 20–24 sn | Telefon değişir, sohbetler **kalır.** | `5_yedek` |
| 24–30 sn | Ördek animasyonu · Ücretsiz. Reklamsız. Açık kaynak. · **Hemen indir** | — |

## Çekim listesi (ekran kayıtları)

**Genel hazırlık**
- Telefonun kendi ekran kaydını kullan (Hızlı ayarlar → *Ekran kaydı*). Ses kapalı, "dokunuşları göster" **kapalı**.
- **Rahatsız etmeyin** açık olsun (kayıt sırasında bildirim düşmesin), şarj dolu görünsün.
- Gerçek e-posta adresleri ve tanımadığın kişiler görünmesin. Güzel isimli, fotoğraflı birkaç arkadaşla çek.
- Her klip **6–10 saniye**. Kayda başla, 1 sn bekle, yavaş ve net hareket et, sonunda 1 sn bekle.
- Videoda her klipten **4 saniye** kullanılır. Hangi saniyeden başlayacağını ve hızını ben ayarlarım.

| Dosya | Ne çekilecek |
| --- | --- |
| `1_sohbet` | Birebir sohbet açık. "Akşam halı saha var mı?" yaz, gönder. Karşıdan "Varım! 🔥" gelsin, tikler okundu olsun. (İkinci bir telefon veya arkadaş lazım.) |
| `2_qr` | Ana ekran → *Kişi ekle* → QR kod ekranı (kendi QR'ın veya tarama). |
| `3_anket` | "Halı Saha" grubu → maç anketi, birkaç oy görünsün → *Takım kur* → takımlar çıksın. |
| `4_tepki` | Bir mesaja uzun bas → ❤️ bırak. Mümkünse karşı taraf "yazıyor…" görünsün, bir fotoğraf gönder. |
| `5_yedek` | Ayarlar → *Sohbet yedeği* anahtarını aç (veya sohbette "sohbet geçmişi gönderildi" notu). |

Kayıtları `promo/clips/` altına bu adlarla koy (`.mp4`, `.mov` veya `.webm`).

## Video üretmek

```bash
python3 -m venv .venv && .venv/bin/pip install pillow numpy imageio-ffmpeg
.venv/bin/python promo/build_promo.py                    # promo/render/dakk_tanitim.mp4 (+ _sessiz)
.venv/bin/python promo/build_promo.py --frames 6,13.5    # yalnızca PNG önizleme
```

Klip ince ayarı `promo/clips/ayar.json`:

```json
{ "1_sohbet": { "start": 1.5, "speed": 1.25 } }
```

- `dakk_tanitim.mp4`: telifsiz, kodla üretilmiş müzikle (120 BPM, sahne geçişleri ölçü başına denk gelir).
- `dakk_tanitim_sessiz.mp4`: Instagram/TikTok'ta uygulamanın kendi müzik kütüphanesinden şarkı eklemek için.

Yazı tipi: Google Sans (SIL Open Font License, `assets/fonts/OFL.txt`); yoksa betik GitHub'dan indirir.
