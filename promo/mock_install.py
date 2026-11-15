"""
"Dakk nasıl indirilir?" videosu için telefon ekranları: Chrome'da site → APK indir →
bilinmeyen kaynaklara izin → yükle → aç (Dakk açılışı ve giriş ekranı).
Android/Chrome arayüzü sade ve markasız çizilir; Dakk ekranları uygulamadakiyle aynı renklerde.
"""
import math
import os
from functools import lru_cache

from PIL import Image, ImageDraw

from mock_screens import (BG, D, SH, SW, TXT2, TXT3, WHITE, Y, avatar, back_out, base, circle, clamp01, dp,
                          ease_in_out, ease_out, emoji_img, icon_back, icon_lock, prog, put, rich, rich_width, rrect,
                          slide_between, status_bar, wrap)

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
LOGO = os.path.join(REPO, "docs", "logo.png")
SPLASH = os.path.join(REPO, "app", "src", "main", "res", "drawable-nodpi", "dakk_splash_{:02d}.webp")

URL = "cnrcrm20-ctrl.github.io/Dakk"
APK = "Dakk-v2.5.0.apk"
APK_MB = 21.6

# Android / Chrome koyu tema (sade, markasız)
CH_BAR = (32, 33, 36)
CH_PILL = (60, 64, 67)
DLG = (43, 45, 50)
SHEET = (41, 42, 45)
BLUE = (138, 180, 248)
BLUE_DARK = (6, 46, 111)
SITE_YELLOW = (255, 193, 7)


@lru_cache(maxsize=8)
def logo(size):
    return Image.open(LOGO).convert("RGBA").resize((size, size), Image.LANCZOS)


def touch(img, x, y, t, at, dur=0.45):
    """Dokunma göstergesi: beyaz yarı saydam daire belirir, basılır, kaybolur."""
    p = (t - at + 0.15) / (dur + 0.15)
    if not 0 < p < 1:
        return
    lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    op = min(p * 5, (1 - p) * 3, 1)
    press = math.sin(clamp01((t - at) / 0.2) * math.pi) if t >= at else 0
    r = dp(22) * (1 - 0.18 * press)
    d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, int(120 * op)),
              outline=(255, 255, 255, int(220 * op)), width=dp(2))
    if t >= at:
        q = clamp01((t - at) / 0.45)
        rr = dp(22) + dp(30) * ease_out(q)
        d.ellipse((x - rr, y - rr, x + rr, y + rr), outline=(255, 255, 255, int(150 * (1 - q))), width=dp(2))
    img.alpha_composite(lay)


def dim(img, a):
    if a > 0:
        img.alpha_composite(Image.new("RGBA", (SW, SH), (0, 0, 0, int(a))))


# ---------------------------------------------------------------- Chrome ve site

CHROME_H = dp(26) + dp(56)


def chrome_bar(img, url_text, focused=False, cursor=False, load=0.0):
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, SW, CHROME_H), fill=CH_BAR)
    y = dp(26)
    # ev simgesi
    hx, hy = dp(14), y + dp(18)
    d.polygon([(hx, hy + dp(9)), (hx + dp(10), hy), (hx + dp(20), hy + dp(9))], outline=WHITE, width=dp(2))
    d.rectangle((hx + dp(3), hy + dp(9), hx + dp(17), hy + dp(20)), outline=WHITE, width=dp(2))
    px, pw = dp(46), SW - dp(46) - dp(70)
    put(img, rrect(pw, dp(40), dp(20), CH_PILL if not focused else (48, 49, 52),
                   outline=(BLUE + (255,)) if focused else None, width=dp(2) if focused else 0), px, y + dp(8))
    if url_text and not focused:
        url_text = url_text.split("/")[0]   # Chrome adres çubuğunda yalnız alan adını gösterir
    if url_text:
        if not focused:
            icon_lock(img, px + dp(12), y + dp(21), dp(13), (220, 220, 220))
        t = rich(url_text, dp(14), 400, WHITE)
        tx = px + (dp(14) if focused else dp(32))
        fw = px + pw - dp(12) - tx
        field = Image.new("RGBA", (fw, dp(40)), (0, 0, 0, 0))
        tw = rich_width(url_text, dp(14), 400)
        off = min(0, fw - tw - dp(6)) if focused else 0
        tmp = Image.new("RGBA", (fw, dp(40)), (0, 0, 0, 0))
        tmp.paste(t, (int(off), (dp(40) - t.height) // 2))
        if not focused and tw > fw:
            # sağ kenarda yumuşak kesilme
            fade = Image.linear_gradient("L").rotate(90).resize((dp(24), dp(40)))
            a = tmp.getchannel("A")
            m = Image.new("L", (fw, dp(40)), 255)
            m.paste(fade, (fw - dp(24), 0))
            from PIL import ImageChops
            tmp.putalpha(ImageChops.multiply(a, m))
        field.alpha_composite(tmp)
        put(img, field, tx, y + dp(8))
        if cursor:
            cx = tx + off + tw + 3
            d.rectangle((cx, y + dp(18), cx + dp(1.5), y + dp(38)), fill=BLUE)
    else:
        t = rich("Ara veya adres yaz", dp(14), 400, (170, 170, 170))
        put(img, t, px + dp(14), y + dp(8) + (dp(40) - t.height) // 2)
        if cursor:
            d.rectangle((px + dp(14), y + dp(18), px + dp(15.5), y + dp(38)), fill=BLUE)
    # sekme sayısı + menü
    tx = SW - dp(60)
    d.rounded_rectangle((tx, y + dp(17), tx + dp(20), y + dp(37)), dp(4), outline=WHITE, width=dp(2))
    one = rich("1", dp(11), 700, WHITE)
    put(img, one, tx + dp(10) - one.width / 2, y + dp(27) - one.height / 2)
    for k in range(3):
        d.ellipse((SW - dp(22), y + dp(17) + k * dp(7), SW - dp(18), y + dp(21) + k * dp(7)), fill=WHITE)
    if 0 < load < 1:
        d.rectangle((0, CHROME_H - dp(3), SW * load, CHROME_H), fill=BLUE)
    status_bar(img, CH_BAR)


BTN_W, BTN_H = dp(214), dp(50)


def site_page(btn_press=0.0, op=1.0):
    """docs/index.html'in telefondaki görünümü. Döner: (görüntü, düğme merkezi)."""
    img = Image.new("RGBA", (SW, SH - CHROME_H), BG + (255,))
    y = dp(34)
    lg = logo(dp(96))
    img.alpha_composite(lg, ((SW - lg.width) // 2, y))
    y += dp(108)
    t = rich("Dakk", dp(36), 700, SITE_YELLOW)
    img.alpha_composite(t, ((SW - t.width) // 2, y))
    y += dp(50)
    for line in wrap("Arkadaşlarınla güvenle yazış. Mesajların yalnızca sizde okunur.", dp(16), 400, SW - dp(48)):
        t = rich(line, dp(16), 400, (189, 189, 189))
        img.alpha_composite(t, ((SW - t.width) // 2, y))
        y += dp(23)
    y += dp(18)
    s = 1 - 0.05 * btn_press
    bw, bh = int(BTN_W * s), int(BTN_H * s)
    b = rrect(bw, bh, bh // 2, SITE_YELLOW).copy()
    lab = rich("Android için indir", dp(16), 700, (17, 17, 17))
    b.alpha_composite(lab, ((bw - lab.width) // 2, (bh - lab.height) // 2))
    bx, by = (SW - bw) // 2, y + (BTN_H - bh) // 2
    img.alpha_composite(b, (bx, by))
    center = (SW / 2, CHROME_H + y + BTN_H / 2)
    y += BTN_H + dp(12)
    for line in wrap("Sürüm 2.5.0 • Ücretsiz • Reklamsız • Açık kaynak • Android 7.0+", dp(12), 400, SW - dp(60)):
        t = rich(line, dp(12), 400, (138, 138, 138))
        img.alpha_composite(t, ((SW - t.width) // 2, y))
        y += dp(17)
    y += dp(30)
    for cp, title, desc in ((0x1F512, "Uçtan uca şifreli", "Mesajlar telefonunda şifrelenir. Sunucu dahil kimse okuyamaz."),
                            (0x1F4F1, "QR kodla ekle", "Telefon numarası yok, rehber yok. Kodu tara ya da davet gönder.")):
        ch = dp(118)
        card = rrect(SW - dp(40), ch, dp(18), (21, 23, 28)).copy()
        card.alpha_composite(emoji_img(cp, dp(26)), (dp(20), dp(18)))
        card.alpha_composite(rich(title, dp(16), 600, WHITE), (dp(20), dp(52)))
        for li, line in enumerate(wrap(desc, dp(12), 400, SW - dp(80))):
            card.alpha_composite(rich(line, dp(12), 400, (170, 170, 170)), (dp(20), dp(78) + li * dp(16)))
        img.alpha_composite(card, (dp(20), y))
        y += ch + dp(14)
    if op < 1:
        img.putalpha(img.getchannel("A").point(lambda v: int(v * op)))
    return img, center


def browser(t_load_done=True, btn_press=0.0, page_op=1.0, url=URL, load=0.0):
    img = base()
    page, center = site_page(btn_press, page_op)
    img.alpha_composite(page, (0, CHROME_H))
    chrome_bar(img, url, load=load)
    return img, center


# ---------------------------------------------------------------- 1) siteye gir

def scene_site(t):
    t = max(t, 0.0)
    t0, t1, t_go = 0.25, 1.35, 1.5
    n = int(len(URL) * clamp01((t - t0) / (t1 - t0)))
    if t < t_go:
        img = base()
        # yeni sekme: sade arama sayfası
        g = rich("Yeni sekme", dp(13), 500, TXT3)
        put(img, g, (SW - g.width) / 2, SH * 0.36)
        chrome_bar(img, URL[:n], focused=True, cursor=(t * 2.2) % 1 < 0.6)
        # klavye
        kb_h = dp(230)
        ky = SH - kb_h
        ImageDraw.Draw(img).rectangle((0, ky, SW, SH), fill=(41, 42, 45))
        rows = ["qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"]
        cur = URL[n - 1].lower() if n > 0 else ""
        for r, row in enumerate(rows):
            kw = (SW - dp(12)) / 12
            ox = (SW - kw * len(row)) / 2
            for k, ch in enumerate(row):
                x0 = ox + k * kw + dp(2)
                y0 = ky + dp(12) + r * dp(50)
                hot = ch == cur and t < t1 + 0.05
                put(img, rrect(int(kw - dp(4)), dp(42), dp(6), (95, 99, 104) if hot else (60, 64, 67)), x0, y0)
                lt = rich(ch, dp(16), 400, WHITE)
                put(img, lt, x0 + (kw - dp(4) - lt.width) / 2, y0 + (dp(42) - lt.height) / 2)
        put(img, rrect(SW - dp(150), dp(42), dp(6), (60, 64, 67)), dp(75), ky + dp(162))
        go = rrect(dp(56), dp(42), dp(6), BLUE)
        put(img, go, SW - dp(66), ky + dp(162))
        ImageDraw.Draw(img).line((SW - dp(48), ky + dp(183), SW - dp(28), ky + dp(183)), fill=BLUE_DARK, width=dp(2))
        ImageDraw.Draw(img).line((SW - dp(35), ky + dp(176), SW - dp(28), ky + dp(183), SW - dp(35), ky + dp(190)),
                                 fill=BLUE_DARK, width=dp(2))
        touch(img, SW - dp(38), ky + dp(183), t, t_go - 0.12, 0.3)
        return img.convert("RGB")
    load = prog(t, t_go, 0.6)
    page_op = ease_out(prog(t, t_go + 0.25, 0.4))
    press = math.sin(prog(t, 3.2, 0.22) * math.pi)
    img, (cx, cy) = browser(btn_press=press, page_op=page_op, load=load)
    touch(img, cx + dp(30), cy + dp(4), t, 3.2)
    return img.convert("RGB")


# ---------------------------------------------------------------- 2) dosyayı indir

def scene_download(t):
    t = max(t, 0.0)
    img, _ = browser()
    sp = min(ease_out(prog(t, 0.0, 0.4)), 1 - ease_in_out(prog(t, 1.45, 0.3)))
    dim(img, 120 * sp)
    if sp > 0:
        sh_h = dp(236)
        sy = SH - sh_h * sp
        put(img, rrect(SW, sh_h + dp(30), dp(28), SHEET, corners=(True, True, False, False)), 0, sy)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((SW / 2 - dp(16), sy + dp(10), SW / 2 + dp(16), sy + dp(14)), dp(2), fill=(95, 99, 104))
        # dosya simgesi
        fx, fy = dp(24), sy + dp(32)
        put(img, rrect(dp(40), dp(48), dp(6), (60, 64, 67)), fx, fy)
        put(img, rich("APK", dp(11), 700, (60, 220, 132)), fx + dp(6), fy + dp(16))
        put(img, rich(APK, dp(17), 600, WHITE), fx + dp(54), fy + dp(2))
        put(img, rich(f"{APK_MB:.1f} MB".replace(".", ","), dp(13), 400, TXT2), fx + dp(54), fy + dp(26))
        for li, line in enumerate(wrap("Bu dosya türü cihazına zarar verebilir. Yine de indirmek istiyor musun?",
                                       dp(14), 400, SW - dp(48))):
            put(img, rich(line, dp(14), 400, (200, 200, 200)), dp(24), sy + dp(96) + li * dp(20))
        by = sy + dp(158)
        c = rich("İptal", dp(15), 600, BLUE)
        put(img, c, SW - dp(24) - dp(150) - dp(24) - c.width, by + (dp(44) - c.height) / 2)
        press = math.sin(prog(t, 1.15, 0.22) * math.pi)
        s = 1 - 0.05 * press
        bw, bh = int(dp(150) * s), int(dp(44) * s)
        b = rrect(bw, bh, bh // 2, BLUE).copy()
        lab = rich("Yine de indir", dp(15), 600, BLUE_DARK)
        b.alpha_composite(lab, ((bw - lab.width) // 2, (bh - lab.height) // 2))
        bx = SW - dp(24) - dp(150) + (dp(150) - bw) / 2
        put(img, b, bx, by + (dp(44) - bh) / 2)
        touch(img, SW - dp(24) - dp(75), by + dp(22), t, 1.15)
    # indirme çubuğu
    dp_ = ease_out(prog(t, 1.75, 0.35))
    if dp_ > 0:
        fill = ease_in_out(prog(t, 1.9, 1.5))
        done = fill >= 1
        bh = dp(72)
        bar = rrect(SW - dp(16), bh, dp(14), (60, 64, 67)).copy()
        bd = ImageDraw.Draw(bar)
        # indirme simgesi
        ix, iy = dp(16), dp(20)
        col = (60, 220, 132) if done else WHITE
        bd.line((ix + dp(10), iy, ix + dp(10), iy + dp(18)), fill=col, width=dp(2))
        bd.line((ix + dp(3), iy + dp(11), ix + dp(10), iy + dp(18), ix + dp(17), iy + dp(11)), fill=col, width=dp(2))
        bd.line((ix + dp(1), iy + dp(26), ix + dp(19), iy + dp(26)), fill=col, width=dp(2))
        title = "İndirme tamamlandı" if done else "İndiriliyor…"
        bar.alpha_composite(rich(title, dp(14), 600, WHITE), (dp(48), dp(12)))
        mb = APK_MB * fill
        sub = APK if done else f"{APK} · {mb:.1f} / {APK_MB:.1f} MB".replace(".", ",").replace(",apk", ".apk")
        bar.alpha_composite(rich(sub, dp(12), 400, (200, 200, 200)), (dp(48), dp(34)))
        if not done:
            pw = SW - dp(16) - dp(64)
            bd.rounded_rectangle((dp(48), bh - dp(12), dp(48) + pw, bh - dp(8)), dp(2), fill=(95, 99, 104))
            bd.rounded_rectangle((dp(48), bh - dp(12), dp(48) + pw * fill, bh - dp(8)), dp(2), fill=BLUE)
        else:
            a = rich("Aç", dp(16), 700, BLUE)
            bar.alpha_composite(a, (bar.width - dp(20) - a.width, (bh - a.height) // 2))
        put(img, bar, dp(8), SH - dp(16) - bh + (1 - dp_) * dp(30), dp_)
        if done:
            touch(img, SW - dp(8) - dp(30), SH - dp(16) - bh / 2, t, 4.0)
    return img.convert("RGB")


# ---------------------------------------------------------------- 3) izin ver

def switch(img, x, y, on_p):
    w, h = dp(52), dp(32)
    col = tuple(int(a + (b - a) * on_p) for a, b in zip((60, 64, 67), BLUE))
    put(img, rrect(w, h, h // 2, col, outline=None if on_p > 0.5 else (140, 144, 150, 255),
                   width=0 if on_p > 0.5 else dp(2)), x, y)
    r = dp(8) + dp(4) * on_p
    cx = x + dp(16) + (w - dp(32)) * on_p
    knob = circle(int(2 * r), BLUE_DARK if on_p > 0.5 else (140, 144, 150))
    put(img, knob, cx - r, y + h / 2 - r)


def _unknown_dialog(t):
    img, _ = browser()
    a = ease_out(prog(t, 0.0, 0.3))
    dim(img, 140 * a)
    w = SW - dp(48)
    lines = wrap("Güvenliğin için telefonunun bu kaynaktan bilinmeyen uygulamaları yüklemesine izin verilmiyor. "
                 "Bunu Ayarlar'dan değiştirebilirsin.", dp(14), 400, w - dp(48))
    h = dp(70) + len(lines) * dp(20) + dp(70)
    dlg = rrect(w, h, dp(28), DLG).copy()
    dlg.alpha_composite(rich("Chrome", dp(20), 500, WHITE), (dp(24), dp(24)))
    for i, line in enumerate(lines):
        dlg.alpha_composite(rich(line, dp(14), 400, (200, 200, 200)), (dp(24), dp(66) + i * dp(20)))
    s1 = rich("İptal", dp(15), 600, BLUE)
    s2 = rich("Ayarlar", dp(15), 600, BLUE)
    dlg.alpha_composite(s2, (w - dp(24) - s2.width, h - dp(44)))
    dlg.alpha_composite(s1, (w - dp(24) - s2.width - dp(32) - s1.width, h - dp(44)))
    s = 0.92 + 0.08 * ease_out(a)
    d2 = dlg.resize((int(w * s), int(h * s)), Image.BILINEAR)
    put(img, d2, (SW - d2.width) / 2, (SH - d2.height) / 2, a)
    bx = (SW - w) / 2 + w - dp(24) - s2.width / 2
    by = (SH - h) / 2 + h - dp(44) + s2.height / 2
    touch(img, bx, by, t, 1.05)
    return img


def _settings(t):
    img = base()
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, SW, SH), fill=(18, 19, 22))
    y = dp(26)
    icon_back(d, dp(18), y + dp(30))
    y += dp(64)
    for line in wrap("Bilinmeyen uygulamaları yükle", dp(28), 400, SW - dp(40)):
        put(img, rich(line, dp(28), 400, WHITE), dp(20), y)
        y += dp(36)
    y += dp(26)
    # uygulama satırı: tarayıcı simgesi (markasız mavi küre)
    gx, gy = dp(20), y
    put(img, circle(dp(44), (66, 133, 244)), gx, gy)
    d = ImageDraw.Draw(img)
    d.ellipse((gx + dp(10), gy + dp(10), gx + dp(34), gy + dp(34)), outline=WHITE, width=dp(2))
    d.line((gx + dp(10), gy + dp(22), gx + dp(34), gy + dp(22)), fill=WHITE, width=dp(2))
    d.ellipse((gx + dp(17), gy + dp(10), gx + dp(27), gy + dp(34)), outline=WHITE, width=dp(2))
    put(img, rich("Chrome", dp(18), 500, WHITE), dp(78), y + dp(10))
    y += dp(70)
    on = ease_in_out(prog(t, 2.45, 0.25))
    row = rrect(SW - dp(32), dp(72), dp(24), (36, 38, 43) if on < 1 else (30, 44, 66))
    put(img, row, dp(16), y)
    put(img, rich("Bu kaynaktan izin ver", dp(17), 500, WHITE), dp(36), y + dp(24))
    switch(img, SW - dp(36) - dp(52), y + dp(20), on)
    touch(img, SW - dp(36) - dp(26), y + dp(36), t, 2.4)
    y += dp(96)
    icon = rrect(dp(22), dp(22), dp(11), (0, 0, 0, 0), outline=TXT2 + (255,), width=dp(2))
    put(img, icon, dp(20), y)
    put(img, rich("i", dp(13), 700, TXT2), dp(28), y + dp(1))
    for i, line in enumerate(wrap("Yalnızca güvendiğin yerlerden uygulama yükle. Dakk'ı her zaman resmi siteden veya "
                                  "GitHub'dan indir.", dp(13), 400, SW - dp(80))):
        put(img, rich(line, dp(13), 400, TXT2), dp(54), y + i * dp(19))
    touch(img, dp(26), dp(26) + dp(30), t, 3.75)
    status_bar(img, (18, 19, 22))
    return img


def scene_permission(t):
    t = max(t, 0.0)
    p = ease_in_out(prog(t, 1.3, 0.4))
    a = _unknown_dialog(t) if p < 1 else None
    b = _settings(t) if p > 0 else None
    return (slide_between(a, b, p) if a is not None and b is not None else (a or b)).convert("RGB")


# ---------------------------------------------------------------- 4) yükle ve aç

@lru_cache(maxsize=30)
def splash_frame(i):
    im = Image.open(SPLASH.format(i)).convert("RGBA")
    r = SW / im.width
    im = im.resize((SW, int(im.height * r)), Image.LANCZOS)
    out = Image.new("RGBA", (SW, SH), (255, 255, 255, 255))
    out.alpha_composite(im, (0, (SH - im.height) // 2))
    return out


def login_screen():
    img = base()
    y = dp(70)
    box = logo(dp(116))
    put(img, box, (SW - box.width) / 2, y)
    y += dp(136)
    t = rich("Dakk", dp(36), 800, Y)
    put(img, t, (SW - t.width) / 2, y)
    y += dp(50)
    t = rich("Arkadaşlarınızla güvenle mesajlaşın.", dp(16), 500, WHITE)
    put(img, t, (SW - t.width) / 2, y)
    y += dp(46)
    for cp, title, sub in ((0x1F512, "Uçtan uca şifreli", "Yalnızca siz ve karşınızdaki okur."),
                           (0x1F4F1, "Kolay kişi ekleme", "QR kod veya davet bağlantısıyla."),
                           (0x1F4AC, "Her yerde senkron", "Çevrimdışıyken bile iletilir.")):
        card = rrect(SW - dp(40), dp(66), dp(16), (27, 29, 36)).copy()
        card.alpha_composite(circle(dp(40), (64, 54, 12)), (dp(12), dp(13)))
        e = emoji_img(cp, dp(20))
        card.alpha_composite(e, (dp(12) + (dp(40) - e.width) // 2, dp(13) + (dp(40) - e.height) // 2))
        card.alpha_composite(rich(title, dp(15), 600, WHITE), (dp(64), dp(12)))
        card.alpha_composite(rich(sub, dp(12), 400, TXT2), (dp(64), dp(36)))
        put(img, card, dp(20), y)
        y += dp(76)
    y += dp(18)
    b = rrect(SW - dp(40), dp(54), dp(16), Y).copy()
    lab = rich("Google ile devam et", dp(16), 700, (0, 0, 0))
    b.alpha_composite(lab, ((b.width - lab.width) // 2, (b.height - lab.height) // 2))
    put(img, b, dp(20), y)
    status_bar(img, BG)
    return img, (SW / 2, y + dp(27))


def scene_install(t):
    t = max(t, 0.0)
    if t < 2.95:
        img, _ = browser()
        dim(img, 140 * ease_out(prog(t, 0.0, 0.3)))
        w = SW - dp(48)
        h = dp(262)
        dlg = rrect(w, h, dp(28), DLG).copy()
        lg = logo(dp(56))
        dlg.alpha_composite(lg, ((w - lg.width) // 2, dp(24)))
        nm = rich("Dakk", dp(20), 600, WHITE)
        dlg.alpha_composite(nm, ((w - nm.width) // 2, dp(90)))
        installing = prog(t, 0.95, 1.1)
        done = installing >= 1
        if t < 0.95:
            msg = "Bu uygulamayı yüklemek istiyor musun?"
        elif not done:
            msg = "Yükleniyor…"
        else:
            msg = "Uygulama yüklendi."
        for i, line in enumerate(wrap(msg, dp(15), 400, w - dp(48))):
            m = rich(line, dp(15), 400, (210, 210, 210))
            dlg.alpha_composite(m, ((w - m.width) // 2, dp(128) + i * dp(21)))
        dd = ImageDraw.Draw(dlg)
        if 0.95 <= t and not done:
            pw = w - dp(64)
            dd.rounded_rectangle((dp(32), dp(180), dp(32) + pw, dp(185)), dp(3), fill=(70, 74, 80))
            dd.rounded_rectangle((dp(32), dp(180), dp(32) + pw * ease_in_out(installing), dp(185)), dp(3), fill=BLUE)
        labels = ("İptal", "Yükle") if t < 0.95 else (("Bitti", "Aç") if done else (None, None))
        if labels[0]:
            s1 = rich(labels[0], dp(15), 600, BLUE)
            s2 = rich(labels[1], dp(15), 600, BLUE)
            dlg.alpha_composite(s2, (w - dp(28) - s2.width, h - dp(46)))
            dlg.alpha_composite(s1, (w - dp(28) - s2.width - dp(36) - s1.width, h - dp(46)))
            bx = (SW - w) / 2 + w - dp(28) - s2.width / 2
            by = (SH - h) / 2 + h - dp(46) + s2.height / 2
        a = ease_out(prog(t, 0.0, 0.3))
        s = 0.92 + 0.08 * a
        d2 = dlg.resize((int(w * s), int(h * s)), Image.BILINEAR)
        put(img, d2, (SW - d2.width) / 2, (SH - d2.height) / 2, a)
        if labels[0]:
            touch(img, bx, by, t, 0.7 if t < 0.95 else 2.6)
        return img.convert("RGB")
    # Dakk açılıyor: ördek animasyonu → giriş ekranı
    if t < 4.15:
        k = min(int((t - 2.95) * 26), 29)
        img = splash_frame(k).copy()
        a = ease_out(prog(t, 2.95, 0.25))
        if a < 1:
            img.putalpha(int(255 * a))
            bgi = base()
            bgi.alpha_composite(img)
            img = bgi
        return img.convert("RGB")
    img, (cx, cy) = login_screen()
    p = ease_out(prog(t, 4.15, 0.35))
    if p < 1:
        sp = splash_frame(29).copy()
        sp.putalpha(int(255 * (1 - p)))
        img.alpha_composite(sp)
    return img.convert("RGB")


SCENE_FUNCS = {
    "i1_site": scene_site,
    "i2_indir": scene_download,
    "i3_izin": scene_permission,
    "i4_yukle": scene_install,
}

if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/install_sheet.png"
    times = [float(x) for x in sys.argv[2].split(",")] if len(sys.argv) > 2 else [0.8, 1.8, 2.6, 3.3, 4.1, 4.8]
    sheet = Image.new("RGB", (len(times) * 320, len(SCENE_FUNCS) * 700), (90, 90, 90))
    for r, fn in enumerate(SCENE_FUNCS.values()):
        for c, tt in enumerate(times):
            sheet.paste(fn(tt).resize((310, 689)), (c * 320, r * 700))
    sheet.save(out)
    print(out)
