#!/usr/bin/env python3
"""
Dakk — 30 saniyelik dikey tanıtım videosu (1080x1920, 30 fps).

Kullanım:
    python3 -m venv .venv && .venv/bin/pip install pillow numpy imageio-ffmpeg qrcode
    .venv/bin/python promo/build_promo.py               # promo/render/ altına iki video üretir
    .venv/bin/python promo/build_promo.py --frames 2,6  # yalnızca o saniyelerden PNG önizleme
    .venv/bin/python promo/build_promo.py --uzun        # 69 saniyelik detaylı sürüm (9 sahne + özet)
    .venv/bin/python promo/build_promo.py --indir       # 30 sn "Dakk nasıl indirilir?" videosu
    .venv/bin/python promo/build_promo.py --kurulum     # 60 sn yükleme + kurulum (uyarılar dahil) videosu

Telefon ekranları promo/mock_screens.py'de çizilir: Dakk arayüzünün sadeleştirilmiş, animasyonlu hali
(Google tanıtım reklamlarındaki gibi). İstenirse bir sahne gerçek ekran kaydıyla değiştirilebilir:
promo/clips/<sahne>.mp4 (1_sohbet, 2_qr, 3_anket, 4_tepki, 5_yedek) konursa o kullanılır; ayarı
promo/clips/ayar.json'da:  {"1_sohbet": {"start": 1.2, "speed": 1.3}}
"""
import argparse
import json
import math
import os
import subprocess
import sys
import urllib.request
import wave

import imageio_ffmpeg
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mock_install  # noqa: E402
import mock_screens  # noqa: E402

SCREEN_FUNCS = dict(mock_screens.SCENE_FUNCS, **mock_install.SCENE_FUNCS)
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

W, H, FPS = 1080, 1920, 30
DURATION = 30.0
BAR = 2.0  # 120 BPM, 4/4: her ölçü 2 sn — sahne geçişleri ölçü başına denk gelir

# Renkler (Google reklam dili: beyaz zemin, koyu gri yazı, tek vurgu rengi)
INK = (31, 31, 31)
SUBINK = (95, 99, 104)
YELLOW = (255, 204, 0)
SCREEN_BG = (18, 18, 18)

FONT_PATH = os.path.join(HERE, "assets", "fonts", "GoogleSans.ttf")
FONT_URL = "https://api.github.com/repos/google/fonts/contents/ofl/googlesans/GoogleSans%5BGRAD,opsz,wght%5D.ttf"
SPLASH = os.path.join(REPO, "app", "src", "main", "res", "drawable-nodpi", "dakk_splash_{:02d}.webp")
CLIPS_DIR = os.path.join(HERE, "clips")
OUT_DIR = os.path.join(HERE, "render")

INTRO = dict(start=0.0, end=4.0, lines=["Mesajların", "sadece", "senin."], hl="senin.")

SCENES = [
    dict(start=4.0, end=8.0, clip="1_sohbet", title="Uçtan uca şifreli.", hl="şifreli.",
         sub="Mesajlarını biz bile okuyamayız.", label="Sohbet", hint="Mesaj yaz, gönder, ✓✓ okundu"),
    dict(start=8.0, end=12.0, clip="2_qr", title="Arkadaşını saniyede ekle.", hl="saniyede",
         sub="QR kodu okut, sohbet başlasın.", label="QR ile ekle", hint="Kişi ekle → QR kod"),
    dict(start=12.0, end=16.0, clip="3_anket", title="Maç akşamı? Takımları Dakk kursun.", hl="Dakk",
         sub="Grup anketleri ve tek dokunuşla takım.", label="Anket + Takım kur", hint="Maç anketi → Takım kur"),
    dict(start=16.0, end=20.0, clip="4_tepki", title="Her şey şifreli. Her şey anında.", hl="anında.",
         sub="Tepkiler, fotoğraflar, yazıyor balonu.", label="Tepki ve fotoğraf", hint="Uzun bas → kalp, fotoğraf gönder"),
    dict(start=20.0, end=24.0, clip="5_yedek", title="Telefon değişir, sohbetler kalır.", hl="kalır.",
         sub="Arkadaşlarından şifreli geri yükleme.", label="Sohbet yedeği", hint="Ayarlar → Sohbet yedeği"),
]

OUTRO = dict(start=24.0, duck_start=24.35, text_start=26.0, words="Ücretsiz. Reklamsız. Açık kaynak.",
             button="Hemen indir", url="cnrcrm20-ctrl.github.io/Dakk")

PHONE_END = OUTRO["start"]   # telefon bu saniyede aşağı iner
RECAP = None                 # özet bölümü (yalnız uzun sürümde)
MUSIC = dict(rhythm_end_bar=12, final_chord=26.2, counter_from_bar=None)
OUT_NAME = "dakk_tanitim"

# ---- 69 saniyelik detaylı sürüm: 9 sahne x 6 sn, "hepsi tek uygulamada" özeti, ördekli kapanış
LONG = dict(
    DURATION=69.0,
    SCENES=[
        dict(clip="1_sohbet", title="Uçtan uca şifreli.", hl="şifreli.", sub="Mesajlarını biz bile okuyamayız.", speed=0.75),
        dict(clip="2_qr", title="Arkadaşını saniyede ekle.", hl="saniyede", sub="QR kodu okut, sohbet başlasın.", speed=0.75),
        dict(clip="6_grup", title="Ekibini tek yerde topla.", hl="topla.", sub="50 kişiye kadar şifreli gruplar."),
        dict(clip="3_anket", title="Maç akşamı? Takımları Dakk kursun.", hl="Dakk",
             sub="Hazır maç anketi, tek dokunuşla takım.", speed=0.75),
        dict(clip="4_tepki", title="Tepkini bırak, anında gör.", hl="anında",
             sub="Uzun bas, tepki bırak. Kim yazıyor, gör.", speed=0.75),
        dict(clip="7_foto", title="Fotoğraflar da korunur.", hl="korunur.", sub="Açıldığı an sunucudan silinir."),
        dict(clip="8_asistan", title="Yanında akıllı bir asistan.", hl="asistan.",
             sub="Mesajına @dakk yaz, yapay zekâ yardım etsin."),
        dict(clip="9_kaybolan", title="Bazı mesajlar iz bırakmasın.", hl="iz", sub="Kaybolan mesajlar: 24 saat veya 7 gün."),
        dict(clip="5_yedek", title="Telefon değişir, sohbetler kalır.", hl="kalır.",
             sub="Arkadaşlarından şifreli geri yükleme.", speed=0.75),
    ],
    SCENE_LEN=6.0,
    RECAP=dict(start=58.0, end=62.0, title="Hepsi tek uygulamada.", hl="tek",
               chips=["🔒 Uçtan uca şifreleme", "📱 QR ile ekle", "👥 Gruplar", "📊 Anketler", "⚽ Takım kur",
                      "❤️ Tepkiler", "📷 Şifreli fotoğraf", "✨ Dakk Asistan", "⏱ Kaybolan mesajlar",
                      "💾 Sohbet yedeği", "🔕 Sessize al", "💬 Yanıtla"]),
    OUTRO=dict(start=62.0, duck_start=62.35, text_start=64.0, words="Ücretsiz. Reklamsız. Açık kaynak.",
               button="Hemen indir", url="cnrcrm20-ctrl.github.io/Dakk"),
    MUSIC=dict(rhythm_end_bar=31, final_chord=64.2, counter_from_bar=14),
    OUT_NAME="dakk_tanitim_69sn",
)


# ---- 30 sn "nasıl indirilir?" videosu: 4 adım x 5 sn
HOWTO = dict(
    INTRO=dict(start=0.0, end=4.0, lines=["Dakk'ı", "30 saniyede", "kur."], hl="30 saniyede"),
    SCENES=[
        dict(clip="i1_site", step=1, title="Siteye gir, İndir'e dokun.", hl="İndir'e",
             sub="cnrcrm20-ctrl.github.io/Dakk"),
        dict(clip="i2_indir", step=2, title="Dosyayı indir.", hl="indir.", sub="Chrome sorarsa: Yine de indir."),
        dict(clip="i3_izin", step=3, title="Yüklemeye izin ver.", hl="izin", sub="Bu kaynaktan izin ver. Bir kez yeter."),
        dict(clip="i4_yukle", step=4, title="Yükle, aç. Hazır!", hl="Hazır!", sub="Google hesabınla gir, yazışmaya başla."),
    ],
    SCENE_LEN=5.0,
    OUTRO=dict(start=24.0, duck_start=24.35, text_start=26.0, words="Ücretsiz. Reklamsız. Açık kaynak.",
               button="cnrcrm20-ctrl.github.io/Dakk", url="Android 7.0 ve üzeri"),
    OUT_NAME="dakk_nasil_indirilir",
)


def use_howto_profile():
    global INTRO, SCENES, OUTRO, PHONE_END, OUT_NAME
    INTRO = HOWTO["INTRO"]
    SCENES = []
    t = INTRO["end"]
    for sc in HOWTO["SCENES"]:
        SCENES.append(dict(sc, start=t, end=t + HOWTO["SCENE_LEN"]))
        t += HOWTO["SCENE_LEN"]
    OUTRO = HOWTO["OUTRO"]
    PHONE_END = OUTRO["start"]
    OUT_NAME = HOWTO["OUT_NAME"]


# ---- 60 sn "yükleme ve kurulum": uyarılar (Chrome, tarama, Play Protect) ve uygulama içi kurulum
SETUP = dict(
    DURATION=60.0,
    INTRO=dict(start=0.0, end=4.0, lines=["Dakk", "kurulumu", "1 dakika."], hl="1 dakika."),
    SCENES=[
        dict(clip="i1_site", step=1, title="Siteye gir, İndir'e dokun.", hl="İndir'e",
             sub="cnrcrm20-ctrl.github.io/Dakk", speed=0.85),
        dict(clip="i2_indir", step=2, title="Uyarı çıkarsa: Yine de indir.", hl="indir.",
             sub="Chrome her APK dosyasında bunu sorar. Normal.", speed=0.85),
        dict(clip="i3_izin", step=3, title="Yüklemeye izin ver.", hl="izin",
             sub="Bu kaynaktan izin ver. Bir kez yeter.", speed=0.85),
        dict(clip="i5_tarama", step=4, title="Yükle'ye dokun.", hl="Yükle'ye",
             sub="Telefonun tarama yaparsa bitmesini bekle."),
        dict(clip="i6_protect", step=5, title="Play Protect uyarısı? Panik yok.", hl="yok.",
             sub="Ayrıntılar'a, sonra Yine de yükle'ye dokun."),
        dict(clip="i7_giris", step=6, title="Dakk'ı aç, Google ile gir.", hl="Google",
             sub="Hesabını seç, gerisi otomatik."),
        dict(clip="i8_sihirbaz", step=7, title="Adını yaz, bildirimlere izin ver.", hl="izin",
             sub="Sohbet yedeğini açık bırak, önerilir."),
        dict(clip="i9_hazir", step=8, title="Bu kadar. Hazırsın!", hl="Hazırsın!",
             sub="İlk arkadaşını ekle, yazışmaya başla."),
    ],
    SCENE_LEN=6.0,
    OUTRO=dict(start=52.0, duck_start=52.35, text_start=54.0, words="Ücretsiz. Reklamsız. Açık kaynak.",
               button="cnrcrm20-ctrl.github.io/Dakk", url="Takılırsan yorumlara yaz."),
    MUSIC=dict(rhythm_end_bar=26, final_chord=54.2, counter_from_bar=14),
    OUT_NAME="dakk_yukleme_kurulum",
)


def use_setup_profile():
    global DURATION, INTRO, SCENES, OUTRO, PHONE_END, MUSIC, OUT_NAME
    DURATION = SETUP["DURATION"]
    INTRO = SETUP["INTRO"]
    SCENES = []
    t = INTRO["end"]
    for sc in SETUP["SCENES"]:
        SCENES.append(dict(sc, start=t, end=t + SETUP["SCENE_LEN"]))
        t += SETUP["SCENE_LEN"]
    OUTRO = SETUP["OUTRO"]
    PHONE_END = OUTRO["start"]
    MUSIC = SETUP["MUSIC"]
    OUT_NAME = SETUP["OUT_NAME"]


def use_long_profile():
    global DURATION, SCENES, OUTRO, PHONE_END, RECAP, MUSIC, OUT_NAME
    DURATION = LONG["DURATION"]
    SCENES = []
    t = INTRO["end"]
    for sc in LONG["SCENES"]:
        SCENES.append(dict(sc, start=t, end=t + LONG["SCENE_LEN"]))
        t += LONG["SCENE_LEN"]
    RECAP = LONG["RECAP"]
    OUTRO = LONG["OUTRO"]
    PHONE_END = RECAP["start"]
    MUSIC = LONG["MUSIC"]
    OUT_NAME = LONG["OUT_NAME"]

# Telefon çerçevesi (ekran 9:20 — çoğu Android telefonun kayıt oranı)
SW, SH = 620, 1378
BEZEL = 16
PW, PH = SW + 2 * BEZEL, SH + 2 * BEZEL
PHONE_TOP = 505


# ---------------------------------------------------------------- yardımcılar

def ease_out(p):
    p = min(max(p, 0.0), 1.0)
    return 1 - (1 - p) ** 3


def ease_in_out(p):
    p = min(max(p, 0.0), 1.0)
    return 4 * p ** 3 if p < 0.5 else 1 - (-2 * p + 2) ** 3 / 2


def prog(t, start, dur):
    return min(max((t - start) / dur, 0.0), 1.0)


def ensure_font():
    if os.path.exists(FONT_PATH):
        return
    os.makedirs(os.path.dirname(FONT_PATH), exist_ok=True)
    print("Google Sans indiriliyor…", file=sys.stderr)
    req = urllib.request.Request(FONT_URL, headers={"Accept": "application/vnd.github.raw"})
    with urllib.request.urlopen(req) as r, open(FONT_PATH, "wb") as f:
        f.write(r.read())


_fonts = {}


def font(size, weight=400):
    key = (size, weight)
    if key not in _fonts:
        f = ImageFont.truetype(FONT_PATH, size)
        f.set_variation_by_axes([18, weight, 0])
        _fonts[key] = f
    return _fonts[key]


def with_opacity(img, op):
    if op >= 0.999:
        return img
    out = img.copy()
    a = out.getchannel("A").point(lambda v: int(v * op))
    out.putalpha(a)
    return out


def paste(canvas, img, x, y, op=1.0):
    if op <= 0.001:
        return
    img = with_opacity(img, op)
    canvas.paste(img, (int(round(x)), int(round(y))), img)


def text_img(text, size, weight, color, pad=6):
    f = font(size, weight)
    l, t, r, b = f.getbbox(text)
    asc, desc = f.getmetrics()
    img = Image.new("RGBA", (r - l + 2 * pad, asc + desc + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((pad - l, pad), text, font=f, fill=color + (255,))
    return img


def rounded_mask(w, h, r, scale=4):
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), r * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


# ---------------------------------------------------------------- yazı düzeni

class Headline:
    """Kelime kelime beliren, vurgulu kelimesinin arkasında sarı işaretleyici kayan başlık."""

    def __init__(self, words, size, weight, max_w, center_y, hl=None, line_h=None, forced_lines=None):
        self.size = size
        self.line_h = line_h or int(size * 1.18)
        space = font(size, weight).getlength(" ")
        lines = forced_lines or self._wrap(words, size, weight, max_w, space)
        self.items = []  # (img, x, y, is_hl)
        total_h = len(lines) * self.line_h
        y0 = center_y - total_h / 2
        for li, line in enumerate(lines):
            imgs = [text_img(w, size, weight, INK) for w in line]
            widths = [font(size, weight).getlength(w) for w in line]
            line_w = sum(widths) + space * (len(line) - 1)
            x = (W - line_w) / 2
            for w, img, wd in zip(line, imgs, widths):
                self.items.append((img, x - 6, y0 + li * self.line_h - 6, w == hl, wd))
                x += wd + space

    @staticmethod
    def _wrap(words, size, weight, max_w, space):
        lines, cur, cur_w = [], [], 0.0
        for w in words:
            wd = font(size, weight).getlength(w)
            if cur and cur_w + space + wd > max_w:
                lines.append(cur)
                cur, cur_w = [], 0.0
            cur_w = wd if not cur else cur_w + space + wd
            cur.append(w)
        if cur:
            lines.append(cur)
        return lines

    def draw(self, canvas, t, start, end, stagger=0.09, dur=0.5, rise=50, exit_dur=0.35):
        exit_p = ease_in_out(prog(t, end - exit_dur, exit_dur))
        if exit_p >= 1:
            return
        n = len(self.items)
        hl_start = start + stagger * (n - 1) + dur * 0.6
        hl_p = ease_in_out(prog(t, hl_start, 0.4))
        d = ImageDraw.Draw(canvas)
        for i, (img, x, y, is_hl, wd) in enumerate(self.items):
            p = ease_out(prog(t, start + i * stagger, dur))
            if p <= 0:
                continue
            dy = (1 - p) * rise - exit_p * 30
            op = p * (1 - exit_p)
            if is_hl and hl_p > 0:
                pad_x = self.size * 0.14
                top = y + 6 + self.size * 0.16 + dy
                bottom = y + 6 + self.size * 1.12 + dy
                x0 = x + 6 - pad_x
                x1 = x0 + (wd + 2 * pad_x) * hl_p
                marker = Image.new("RGBA", (int(x1 - x0) + 2, int(bottom - top) + 2), (0, 0, 0, 0))
                ImageDraw.Draw(marker).rounded_rectangle(
                    (0, 0, marker.width - 1, marker.height - 1), int(self.size * 0.16), fill=YELLOW + (255,))
                paste(canvas, marker, x0, top, op)
            paste(canvas, img, x, y + dy, op)


# ---------------------------------------------------------------- arka plan ve telefon

def make_background():
    bg = Image.new("RGB", (W, H), (255, 255, 255))
    glow = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(glow)
    d.ellipse((-420, -260, 520, 680), fill=70)
    d.ellipse((560, 1360, 1500, 2300), fill=60)
    glow = glow.filter(ImageFilter.GaussianBlur(170))
    tint = Image.new("RGB", (W, H), (255, 236, 160))
    return Image.composite(tint, bg, glow)


def make_phone_parts():
    body = Image.new("RGBA", (PW, PH), (0, 0, 0, 0))
    body_mask = rounded_mask(PW, PH, 74)
    solid = Image.new("RGBA", (PW, PH), (24, 24, 26, 255))
    body.paste(solid, (0, 0), body_mask)
    # ince açık kenar çizgisi
    edge = Image.new("L", (PW * 4, PH * 4), 0)
    ImageDraw.Draw(edge).rounded_rectangle((2, 2, PW * 4 - 3, PH * 4 - 3), 74 * 4, outline=255, width=7)
    edge = edge.resize((PW, PH), Image.LANCZOS)
    body.paste(Image.new("RGBA", (PW, PH), (90, 90, 96, 255)), (0, 0), edge)

    screen_mask = rounded_mask(SW, SH, 60)

    overlay = Image.new("RGBA", (PW, PH), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    cx, cy, r = PW // 2, BEZEL + 34, 13  # ön kamera deliği
    od.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(8, 8, 10, 255))

    margin = 120
    shadow = Image.new("L", (PW + 2 * margin, PH + 2 * margin), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((margin, margin + 40, margin + PW, margin + PH + 20), 74, fill=95)
    shadow = shadow.filter(ImageFilter.GaussianBlur(45))
    shadow_img = Image.new("RGBA", shadow.size, (60, 50, 20, 255))
    shadow_img.putalpha(shadow)
    return body, screen_mask, overlay, shadow_img, margin


class ClipReader:
    """Ekran kaydını ekran boyutuna kırpıp sırayla kare kare okur; bitince son karede durur."""

    def __init__(self, path, start, speed, n_frames):
        vf = (f"setpts=PTS/{speed},fps={FPS},scale={SW}:{SH}:force_original_aspect_ratio=increase,"
              f"crop={SW}:{SH}")
        cmd = [FFMPEG, "-v", "error", "-ss", str(start), "-i", path, "-an", "-vf", vf,
               "-frames:v", str(n_frames), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        self.proc = subprocess.Popen(cmd, stdout=subprocess.PIPE)
        self.last = Image.new("RGB", (SW, SH), SCREEN_BG)
        self.done = False

    def next(self):
        if not self.done:
            buf = self.proc.stdout.read(SW * SH * 3)
            if len(buf) == SW * SH * 3:
                self.last = Image.frombytes("RGB", (SW, SH), buf)
            else:
                self.done = True
                self.proc.wait()
        return self.last


def find_clip(name):
    for ext in (".mp4", ".mov", ".webm", ".mkv", ".MP4", ".MOV"):
        p = os.path.join(CLIPS_DIR, name + ext)
        if os.path.exists(p):
            return p
    return None


def load_splash():
    frames = []
    for i in range(30):
        im = Image.open(SPLASH.format(i)).convert("RGB")
        a = np.asarray(im).astype(np.float32)
        # zemindeki hafif gri tonları ve çerçeve izini tam beyaza çek
        lo = a.min(axis=2, keepdims=True)
        k = np.clip((lo - 200) / 40, 0, 1)
        a = a * (1 - k) + 255 * k
        im = Image.fromarray(a.astype(np.uint8)).resize((684, 1216), Image.LANCZOS)
        im = im.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
        frames.append(im)
    return frames


def make_chips(labels, size=44, top=880, max_w=1000, gap_x=20, gap_y=26):
    """Google tarzı çipler (beyaz, ince gri kenarlı, emoji destekli) → [(img, x, y)] ortalanmış satırlar."""
    chips = []
    for lab in labels:
        t = mock_screens.rich(lab, size, 500, INK)
        w, h = t.width + 64, int(size * 2.35)
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        big = Image.new("RGBA", (w * 3, h * 3), (0, 0, 0, 0))
        ImageDraw.Draw(big).rounded_rectangle((0, 0, w * 3 - 1, h * 3 - 1), h * 3 // 2, fill=(255, 255, 255, 255),
                                              outline=(218, 220, 224, 255), width=6)
        img = big.resize((w, h), Image.LANCZOS)
        img.alpha_composite(t, (32, (h - t.height) // 2))
        chips.append(img)
    rows, cur, cur_w = [], [], 0
    for c in chips:
        if cur and cur_w + gap_x + c.width > max_w:
            rows.append(cur)
            cur, cur_w = [], 0
        cur_w = c.width if not cur else cur_w + gap_x + c.width
        cur.append(c)
    if cur:
        rows.append(cur)
    out = []
    y = top
    for row in rows:
        rw = sum(c.width for c in row) + gap_x * (len(row) - 1)
        x = (W - rw) / 2
        for c in row:
            out.append((c, x, y))
            x += c.width + gap_x
        y += row[0].height + gap_y
    return out


# ---------------------------------------------------------------- müzik

def make_music(path, sr=44100):
    """120 BPM, C–G–Am–F; marimba benzeri arpej, yumuşak pad, bas, kick/clap/shaker. Telifsiz."""
    n = int(DURATION * sr)
    out = np.zeros(n, dtype=np.float64)
    beat = 0.5
    t_all = np.arange(n) / sr

    def note_hz(m):
        return 440.0 * 2 ** ((m - 69) / 12)

    def add(sig, at, gain=1.0):
        i = int(at * sr)
        if i >= n:
            return
        j = min(n, i + len(sig))
        out[i:j] += sig[: j - i] * gain

    def pluck(m, length=0.9):
        t = np.arange(int(length * sr)) / sr
        f = note_hz(m)
        env = np.exp(-t * 7.5) * (1 - np.exp(-t * 900))
        s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 4.0 * t) * np.exp(-t * 22)
        s += 0.12 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t * 12)
        return s * env

    def pad(ms, length):
        t = np.arange(int(length * sr)) / sr
        env = np.minimum(t / 0.35, 1) * np.minimum((length - t) / 0.4, 1)
        s = np.zeros_like(t)
        for m in ms:
            f = note_hz(m)
            for det in (-0.12, 0.0, 0.12):
                ff = f * 2 ** (det / 12)
                s += np.sin(2 * np.pi * ff * t) + 0.25 * np.sin(2 * np.pi * 2 * ff * t) + 0.08 * np.sin(2 * np.pi * 3 * ff * t)
        return s * np.clip(env, 0, 1) / (len(ms) * 3)

    def bass(m, length=0.42):
        t = np.arange(int(length * sr)) / sr
        f = note_hz(m)
        env = np.minimum(t / 0.01, 1) * np.exp(-t * 4)
        return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2 * f * t)) * env

    rng = np.random.default_rng(7)

    def kick():
        t = np.arange(int(0.35 * sr)) / sr
        f = 45 + 95 * np.exp(-t * 28)
        ph = 2 * np.pi * np.cumsum(f) / sr
        return np.sin(ph) * np.exp(-t * 9)

    def clap():
        t = np.arange(int(0.22 * sr)) / sr
        nz = rng.standard_normal(len(t))
        nz = np.diff(np.concatenate([[0], nz]))  # kaba yüksek geçiren
        env = np.exp(-t * 26) + 0.5 * np.exp(-((t - 0.012) * 400) ** 2)
        return nz * env * 0.5

    def shaker():
        t = np.arange(int(0.06 * sr)) / sr
        nz = np.diff(np.concatenate([[0], rng.standard_normal(len(t))]))
        nz = np.diff(np.concatenate([[0], nz]))
        return nz * np.exp(-t * 70) * 0.18

    chords = [  # (kök, akor notaları)
        (48, [60, 64, 67, 72]),  # C
        (43, [59, 62, 67, 71]),  # G
        (45, [60, 64, 69, 72]),  # Am
        (41, [60, 65, 69, 72]),  # F
    ]
    arp = [0, 2, 1, 3, 2, 1, 3, 2]
    bars = int(DURATION / BAR)
    for b in range(bars):
        t0 = b * BAR
        root, notes = chords[b % 4]
        rend = MUSIC["rhythm_end_bar"]
        full = 2 <= b < rend        # ritim 4. saniyede girer, final bölümünde çekilir
        outro = b >= rend
        add(pad(notes, BAR + 0.3), t0, 0.16 if not outro else 0.2)
        cf = MUSIC.get("counter_from_bar")
        if cf is not None and full and b >= cf:
            # ikinci yarıda üstte karşı ezgi: uzun videoda tekdüzeliği kırar
            add(pluck(notes[3] + 12, 1.2), t0, 0.075)
            add(pluck(notes[2] + 12, 1.0), t0 + 0.75, 0.06)
            add(pluck(notes[1] + 12, 1.0), t0 + 1.5, 0.06)
        if cf is not None and b == cf - 1:
            t_r = np.arange(int(0.9 * sr)) / sr
            add(np.diff(np.concatenate([[0], rng.standard_normal(len(t_r))])) * (t_r / 0.9) ** 2 * 0.2, t0 + 1.1)
        if b < rend + 1:
            for k in range(8):
                m = notes[arp[k]] + (12 if (k % 4 == 3 and full) else 0)
                add(pluck(m), t0 + k * beat / 2, 0.16 if full else 0.13)
        if full:
            for k in range(4):
                add(kick(), t0 + k * beat, 0.55 if k % 2 == 0 else 0.0)
                if k % 2 == 1:
                    add(clap(), t0 + k * beat, 0.45)
                add(bass(root), t0 + k * beat, 0.22)
                add(bass(root + 12, 0.2), t0 + k * beat + beat / 2, 0.1)
            for k in range(16):
                add(shaker(), t0 + k * beat / 4, 0.9 if k % 2 else 0.5)
    # 4. saniyeye doğru yükselen hışırtı
    t = np.arange(int(0.9 * sr)) / sr
    riser = np.diff(np.concatenate([[0], rng.standard_normal(len(t))])) * (t / 0.9) ** 2 * 0.25
    add(riser, 3.1)
    # final akoru: ördek göz kırptığında
    for m in (48, 55, 60, 64, 67, 72, 76):
        add(pluck(m, 3.0), MUSIC["final_chord"], 0.14)
    add(kick(), MUSIC["final_chord"], 0.5)
    # basit yankı
    wet = np.zeros_like(out)
    for d, g in ((0.075, 0.28), (0.137, 0.2), (0.25, 0.14), (0.375, 0.08)):
        k = int(d * sr)
        wet[k:] += out[:-k] * g
    mix = out + wet
    # son 1.5 sn yavaşça kıs
    fade = np.clip((DURATION - t_all) / 1.5, 0, 1)
    mix *= fade
    mix = np.tanh(mix * 1.6) / np.tanh(1.6)
    mix = mix / (np.abs(mix).max() + 1e-9) * 0.89
    stereo = np.stack([mix, np.roll(mix, int(0.008 * sr)) * 0.98], axis=1)
    pcm = (stereo * 32767).astype(np.int16)
    with wave.open(path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(pcm.tobytes())


# ---------------------------------------------------------------- render

class Renderer:
    def __init__(self):
        ensure_font()
        self.bg = make_background()
        self.body, self.screen_mask, self.overlay, self.shadow, self.shadow_margin = make_phone_parts()
        self.splash = load_splash()
        try:
            settings = json.load(open(os.path.join(CLIPS_DIR, "ayar.json"), encoding="utf-8"))
        except (OSError, ValueError):
            settings = {}
        self.settings = settings
        self.intro = Headline(None, 150, 600, 980, 900, hl=INTRO["hl"], line_h=176,
                              forced_lines=[[w] for w in INTRO["lines"]])
        self.titles = [Headline(s["title"].split(), 84, 600, 960, 285, hl=s["hl"], line_h=100) for s in SCENES]
        self.recap = None
        if RECAP:
            self.recap_title = Headline(RECAP["title"].split(), 100, 600, 980, 640, hl=RECAP["hl"], line_h=118)
            self.recap = make_chips(RECAP["chips"])
        self.subs = [text_img(s["sub"], 40, 400, SUBINK) for s in SCENES]
        self.readers = {}
        self.outro_words = Headline(OUTRO["words"].split(), 60, 600, 980, 1475)
        bf = font(50, 600)
        bw = int(bf.getlength(OUTRO["button"])) + 120
        self.button = Image.new("RGBA", (bw, 116), (0, 0, 0, 0))
        bd = ImageDraw.Draw(self.button)
        bd.rounded_rectangle((0, 0, bw - 1, 115), 58, fill=YELLOW + (255,))
        bd.text((60, 29), OUTRO["button"], font=bf, fill=INK + (255,))
        self.url = text_img(OUTRO["url"], 36, 400, SUBINK)

    def draw_step(self, canvas, t, n, start, end):
        """Adım rozeti: sarı daire içinde numara + "/4"."""
        p = mock_screens.back_out(prog(t, start, 0.45))
        ex = ease_in_out(prog(t, end - 0.35, 0.35))
        if p <= 0 or ex >= 1:
            return
        size = 92
        b = Image.new("RGBA", (size * 3, size * 3), (0, 0, 0, 0))
        ImageDraw.Draw(b).ellipse((0, 0, size * 3 - 1, size * 3 - 1), fill=YELLOW + (255,))
        b = b.resize((size, size), Image.LANCZOS)
        num = text_img(str(n), 54, 700, INK)
        b.alpha_composite(num, ((size - num.width) // 2, (size - num.height) // 2 - 2))
        s = max(0.01, min(p, 1.1))
        b2 = b.resize((max(2, int(size * s)), max(2, int(size * s))), Image.BILINEAR)
        tot = len(SCENES)
        cx, cy = W / 2, 118
        paste(canvas, b2, cx - b2.width / 2, cy - b2.height / 2 - ex * 20, 1 - ex)

    def screen_for(self, idx, t):
        s = SCENES[idx]
        path = find_clip(s["clip"])
        if not path:
            return SCREEN_FUNCS[s["clip"]]((t - s["start"]) * s.get("speed", 1.0))
        if idx not in self.readers:
            cfg = self.settings.get(s["clip"], {})
            n = int((s["end"] - s["start"] + 1.0) * FPS)
            self.readers[idx] = ClipReader(path, float(cfg.get("start", 0)), float(cfg.get("speed", 1)), n)
        return self.readers[idx].next()

    def phone_screen(self, t):
        # Sahne sınırında 0.3 sn çapraz geçiş
        idx = 0
        for i, s in enumerate(SCENES):
            if t >= s["start"]:
                idx = i
        cur = self.screen_for(idx, t)
        if idx + 1 < len(SCENES):
            nxt_start = SCENES[idx + 1]["start"]
            if t >= nxt_start - 0.3:
                nxt = self.screen_for(idx + 1, t)
                p = ease_in_out(prog(t, nxt_start - 0.3, 0.3))
                return Image.blend(cur, nxt, p)
        return cur

    def frame(self, t):
        canvas = self.bg.copy()

        # --- giriş yazısı
        if t < INTRO["end"]:
            self.intro.draw(canvas, t, 0.25, INTRO["end"] - 0.25, stagger=0.38, dur=0.6, rise=70, exit_dur=0.4)

        # --- telefon
        enter = ease_out(prog(t, 3.55, 0.75))
        leave = ease_in_out(prog(t, PHONE_END - 0.1, 0.55))
        if enter > 0 and leave < 1:
            cur = [s for s in SCENES if s["start"] <= t < s["end"]]
            if cur:
                ln = cur[0]["end"] - cur[0]["start"]
                local = (t - cur[0]["start"]) / ln
            else:
                local = 0
            scale = 0.94 + 0.06 * enter + 0.018 * ease_in_out(local)
            dy = (1 - enter) * 1500 + leave * 1600 + math.sin(t * 1.3) * 5
            phone = self.body.copy()
            phone.paste(self.phone_screen(max(t, SCENES[0]["start"])), (BEZEL, BEZEL), self.screen_mask)
            phone.alpha_composite(self.overlay)
            shadow = self.shadow
            if abs(scale - 1) > 1e-3:
                phone = phone.resize((int(PW * scale), int(PH * scale)), Image.BILINEAR)
                shadow = shadow.resize((int(shadow.width * scale), int(shadow.height * scale)), Image.BILINEAR)
            x = (W - phone.width) / 2
            y = PHONE_TOP + dy + (PH - phone.height) / 2
            m = self.shadow_margin * scale
            paste(canvas, shadow, x - m, y - m)
            paste(canvas, phone, x, y)

        # --- sahne başlıkları
        for i, s in enumerate(SCENES):
            if s["start"] - 0.2 <= t < s["end"]:
                start = s["start"] + (0.35 if i == 0 else 0.05)
                if s.get("step"):
                    self.draw_step(canvas, t, s["step"], start, s["end"])
                self.titles[i].draw(canvas, t, start + (0.12 if s.get("step") else 0), s["end"])
                sub = self.subs[i]
                p = ease_out(prog(t, start + 0.3, 0.5))
                ex = ease_in_out(prog(t, s["end"] - 0.35, 0.35))
                last_y = max(it[2] for it in self.titles[i].items)
                paste(canvas, sub, (W - sub.width) / 2, last_y + 118 + (1 - p) * 30 - ex * 30, p * (1 - ex))

        # --- özet: "Hepsi tek uygulamada." + özellik çipleri
        if self.recap and RECAP["start"] - 0.1 <= t < RECAP["end"]:
            rs, re_ = RECAP["start"] + 0.25, RECAP["end"]
            self.recap_title.draw(canvas, t, rs, re_, stagger=0.1)
            ex = ease_in_out(prog(t, re_ - 0.4, 0.4))
            for i, (chip, x, y) in enumerate(self.recap):
                p = ease_out(prog(t, rs + 0.45 + i * 0.09, 0.45))
                if p <= 0:
                    continue
                s = 0.8 + 0.2 * mock_screens.back_out(prog(t, rs + 0.45 + i * 0.09, 0.45))
                c = chip.resize((max(2, int(chip.width * s)), max(2, int(chip.height * s))), Image.BILINEAR)
                paste(canvas, c, x + (chip.width - c.width) / 2, y + (chip.height - c.height) / 2 + (1 - p) * 40 - ex * 30,
                      p * (1 - ex))

        # --- kapanış
        if t >= OUTRO["duck_start"]:
            k = min(int((t - OUTRO["duck_start"]) * 15), 29)
            duck = self.splash[k]
            p = ease_out(prog(t, OUTRO["duck_start"], 0.25))
            # çarpma karışımı: beyaz zemin kaybolur, çizgiler arka planın üstüne oturur
            x, y = int((W - duck.width) / 2), int(150 + (1 - p) * 40)
            box = (x, y, x + duck.width, y + duck.height)
            region = canvas.crop(box)
            mixed = ImageChops.multiply(region, duck)
            canvas.paste(Image.blend(region, mixed, p), box)
            self.outro_words.draw(canvas, t, OUTRO["text_start"], DURATION + 1, stagger=0.12)
            bp = ease_out(prog(t, OUTRO["text_start"] + 0.55, 0.55))
            bs = 0.85 + 0.15 * bp
            btn = self.button.resize((int(self.button.width * bs), int(self.button.height * bs)), Image.BILINEAR)
            paste(canvas, btn, (W - btn.width) / 2, 1600 + (116 - btn.height) / 2 + (1 - bp) * 30, bp)
            up = ease_out(prog(t, OUTRO["text_start"] + 0.85, 0.5))
            paste(canvas, self.url, (W - self.url.width) / 2, 1752, up)

        # son 0.15 sn'de beyaza değil, olduğu gibi biter (döngüde temiz görünür)
        return canvas


def render(args):
    os.makedirs(OUT_DIR, exist_ok=True)
    r = Renderer()
    if args.frames:
        for s in args.frames.split(","):
            t = float(s)
            p = os.path.join(OUT_DIR, f"onizleme_{OUT_NAME}_{t:05.2f}.png")
            r.frame(t).save(p)
            print(p)
        return

    music = os.path.join(OUT_DIR, f"muzik_{OUT_NAME}.wav")
    make_music(music)
    silent = os.path.join(OUT_DIR, f"{OUT_NAME}_sessiz.mp4")
    enc = subprocess.Popen(
        [FFMPEG, "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
         "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
         "-movflags", "+faststart", silent],
        stdin=subprocess.PIPE)
    total = int(DURATION * FPS)
    for i in range(total):
        enc.stdin.write(r.frame(i / FPS).tobytes())
        if i % 60 == 0:
            print(f"  {i / FPS:4.1f} sn", file=sys.stderr, flush=True)
    enc.stdin.close()
    enc.wait()
    final = os.path.join(OUT_DIR, f"{OUT_NAME}.mp4")
    subprocess.run([FFMPEG, "-y", "-v", "error", "-i", silent, "-i", music, "-c:v", "copy", "-c:a", "aac",
                    "-b:a", "192k", "-shortest", "-movflags", "+faststart", final], check=True)
    print(final)
    print(silent)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", help="Virgülle ayrılmış saniyeler: yalnızca PNG önizleme üretir")
    ap.add_argument("--uzun", action="store_true", help="69 saniyelik detaylı sürüm")
    ap.add_argument("--indir", action="store_true", help="30 sn 'nasıl indirilir' videosu")
    ap.add_argument("--kurulum", action="store_true", help="60 sn yükleme + kurulum videosu")
    a = ap.parse_args()
    ap_indir = a.indir
    if a.uzun:
        use_long_profile()
    elif ap_indir:
        use_howto_profile()
    elif a.kurulum:
        use_setup_profile()
    render(a)
