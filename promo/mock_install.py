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

# ================================================================ 60 sn "yükleme ve kurulum" videosu için ek sahneler

from mock_screens import SURF, SURFV, BORDER, icon_checks, TXT3 as _TXT3  # noqa: E402

GREEN = (129, 201, 149)


def system_dialog(w, title, body_lines, buttons, icon=None, extra=None, title_size=20):
    """Android tarzı koyu iletişim kutusu → (görüntü, {düğme: (x, y) merkez})."""
    pad = dp(24)
    h = pad + (dp(44) if icon else 0) + dp(30) + len(body_lines) * dp(20) + (extra[1] if extra else 0) + dp(70)
    img = rrect(w, h, dp(28), DLG).copy()
    y = pad
    if icon:
        icon(img, (w - dp(32)) // 2, y)
        y += dp(44)
    tl = wrap(title, dp(title_size), 500, w - 2 * pad)
    h_extra = (len(tl) - 1) * dp(26)
    if h_extra:
        img2 = rrect(w, h + h_extra, dp(28), DLG).copy()
        img2.alpha_composite(img.crop((0, 0, w, y)), (0, 0))
        img, h = img2, h + h_extra
    for line in tl:
        t = rich(line, dp(title_size), 500, WHITE)
        img.alpha_composite(t, ((w - t.width) // 2 if icon else pad, y))
        y += dp(26)
    y += dp(6)
    for line in body_lines:
        img.alpha_composite(rich(line, dp(14), 400, (200, 200, 200)), (pad, y))
        y += dp(20)
    if extra:
        extra[0](img, pad, y)
        y += extra[1]
    pos = {}
    x = w - pad
    for b in reversed(buttons):
        t = rich(b, dp(15), 600, BLUE)
        x -= t.width
        img.alpha_composite(t, (x, h - dp(46)))
        pos[b] = (x + t.width / 2, h - dp(46) + t.height / 2)
        x -= dp(32)
    return img, pos


def place_dialog(img, dlg, pos, a):
    s = 0.92 + 0.08 * ease_out(a)
    d2 = dlg.resize((max(2, int(dlg.width * s)), max(2, int(dlg.height * s))), Image.BILINEAR)
    x0, y0 = (SW - d2.width) / 2, (SH - d2.height) / 2
    put(img, d2, x0, y0, clamp01(a))
    ox, oy = (SW - dlg.width) / 2, (SH - dlg.height) / 2
    return {k: (ox + v[0], oy + v[1]) for k, v in pos.items()}


def shield_icon(color):
    def draw(img, x, y):
        ss = 4
        S = dp(32) * ss
        big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(big)
        d.polygon([(S * 0.5, S * 0.04), (S * 0.9, S * 0.2), (S * 0.86, S * 0.58), (S * 0.5, S * 0.96),
                   (S * 0.14, S * 0.58), (S * 0.1, S * 0.2)], fill=color + (255,))
        d.line((S * 0.32, S * 0.5, S * 0.45, S * 0.64, S * 0.7, S * 0.36), fill=DLG + (255,), width=int(S * 0.09))
        img.alpha_composite(big.resize((dp(32), dp(32)), Image.LANCZOS), (int(x), int(y)))
    return draw


def highlight(img, x, y, w, h, t, start, dur=0.8):
    """Dikkat çekmek için sarı, nabız gibi atan çerçeve."""
    p = (t - start) / dur
    if not 0 < p < 1:
        return
    k = 0.5 + 0.5 * math.sin(p * math.pi * 4)
    g = dp(4) * k
    op = min(p * 6, (1 - p) * 6, 1)
    lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    ImageDraw.Draw(lay).rounded_rectangle((x - g, y - g, x + w + g, y + h + g), dp(12) + g,
                                          outline=Y + (int(255 * op),), width=dp(3))
    img.alpha_composite(lay)


INSTALL_W = SW - dp(48)


def install_dialog(t_state, msg, labels, progress=None, scan=None):
    """Paket yükleyici penceresi (Dakk simgesi, ad, mesaj, düğmeler)."""
    w = INSTALL_W
    h = dp(262) if scan is None else dp(330)
    dlg = rrect(w, h, dp(28), DLG).copy()
    lg = logo(dp(56))
    dlg.alpha_composite(lg, ((w - lg.width) // 2, dp(24)))
    nm = rich("Dakk", dp(20), 600, WHITE)
    dlg.alpha_composite(nm, ((w - nm.width) // 2, dp(90)))
    y = dp(128)
    for line in wrap(msg, dp(15), 400, w - dp(48)):
        m = rich(line, dp(15), 400, (210, 210, 210))
        dlg.alpha_composite(m, ((w - m.width) // 2, y))
        y += dp(21)
    dd = ImageDraw.Draw(dlg)
    if progress is not None:
        pw = w - dp(64)
        dd.rounded_rectangle((dp(32), dp(180), dp(32) + pw, dp(185)), dp(3), fill=(70, 74, 80))
        dd.rounded_rectangle((dp(32), dp(180), dp(32) + pw * progress, dp(185)), dp(3), fill=BLUE)
    if scan is not None:
        scan(dlg, w, dp(160))
    pos = {}
    if labels:
        x = w - dp(28)
        for b in reversed(labels):
            tt = rich(b, dp(15), 600, BLUE)
            x -= tt.width
            dlg.alpha_composite(tt, (x, h - dp(46)))
            pos[b] = (x + tt.width / 2, h - dp(46) + tt.height / 2)
            x -= dp(36)
    return dlg, pos


# ---------------------------------------------------------------- 4) yükle + güvenlik taraması

def scene_install_scan(t):
    t = max(t, 0.0)
    img, _ = browser()
    dim(img, 140 * ease_out(prog(t, 0.0, 0.3)))
    if t < 1.0:
        dlg, pos = install_dialog(t, "Bu uygulamayı yüklemek istiyor musun?", ("İptal", "Yükle"))
        p = place_dialog(img, dlg, pos, ease_out(prog(t, 0.0, 0.3)))
        touch(img, *p["Yükle"], t, 0.7)
        return img.convert("RGB")
    sp = prog(t, 1.1, 2.0)
    found = t >= 3.15

    def scan(dlg, w, y):
        d = ImageDraw.Draw(dlg)
        cx, cy, r = w / 2, y + dp(58), dp(40)
        if not found:
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(70, 74, 80), width=dp(4))
            a0 = (t * 300) % 360
            d.arc((cx - r, cy - r, cx + r, cy + r), a0, a0 + 100, fill=BLUE, width=dp(4))
            pc = rich(f"%{int(sp * 100)}", dp(16), 700, WHITE)
            dlg.alpha_composite(pc, (int(cx - pc.width / 2), int(cy - pc.height / 2)))
            lab = rich("Güvenlik taraması…", dp(14), 500, (200, 200, 200))
        else:
            pop = back_out(prog(t, 3.15, 0.35))
            rr = r * min(pop, 1.1)
            d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=GREEN)
            if pop > 0.5:
                ss = 4
                S = dp(40) * ss
                big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
                ImageDraw.Draw(big).line((S * 0.22, S * 0.52, S * 0.42, S * 0.72, S * 0.8, S * 0.3),
                                         fill=(20, 40, 25, 255), width=int(S * 0.11), joint="curve")
                dlg.alpha_composite(big.resize((dp(40), dp(40)), Image.LANCZOS), (int(cx - dp(20)), int(cy - dp(20))))
            lab = rich("Risk bulunamadı", dp(15), 600, GREEN)
        dlg.alpha_composite(lab, (int(cx - lab.width / 2), int(cy + r + dp(12))))

    dlg, pos = install_dialog(t, "Yüklemeden önce kontrol ediliyor", None, scan=scan)
    pp = min(1.0, 1 - prog(t, 4.0, 0.25))
    place_dialog(img, dlg, pos, pp)
    if t >= 4.1:
        draw_protect(img, t - 4.1, expanded=False)
    return img.convert("RGB")


# ---------------------------------------------------------------- 5) Play Protect

PROTECT_BODY = "Play Protect bu uygulamanın geliştiricisini tanımıyor. Bilinmeyen geliştiricilerin " \
               "uygulamaları bazen güvenli olmayabilir."


def draw_protect(img, lt, expanded, t_tap_more=None, t_tap_install=None, gt=None):
    w = SW - dp(40)
    body = wrap(PROTECT_BODY, dp(14), 400, w - dp(48))
    ex_h = dp(56)

    def extra(dlg, x, y):
        d = ImageDraw.Draw(dlg)
        tt = rich("Ayrıntılar", dp(14), 600, BLUE)
        dlg.alpha_composite(tt, (x, y + dp(14)))
        ax, ay = x + tt.width + dp(10), y + dp(25)
        if expanded:
            d.line((ax, ay + dp(3), ax + dp(6), ay - dp(3), ax + dp(12), ay + dp(3)), fill=BLUE, width=dp(2))
        else:
            d.line((ax, ay - dp(3), ax + dp(6), ay + dp(3), ax + dp(12), ay - dp(3)), fill=BLUE, width=dp(2))

    dlg, pos = system_dialog(w, "Play Protect tarafından engellendi", body, ["Tamam"],
                             icon=shield_icon(BLUE), extra=(extra, ex_h + (dp(54) if expanded else 0)), title_size=19)
    if expanded:
        # "Yine de yükle" seçeneği ayrıntıların altında
        yy = dlg.height - dp(70) - dp(54)
        tt = rich("Yine de yükle (güvenli değil)", dp(14), 600, BLUE)
        dlg.alpha_composite(tt, (dp(24), yy + dp(12)))
        pos["Yine de yükle"] = (dp(24) + tt.width / 2, yy + dp(12) + tt.height / 2)
        pos["_yy"] = (dp(16), yy)
    a = ease_out(clamp01(lt / 0.3))
    p = place_dialog(img, dlg, pos, a)
    return p, ((SW - w) / 2, (SH - dlg.height) / 2, w, dlg.height)


def scene_protect(t):
    t = max(t, 0.0)
    img, _ = browser()
    dim(img, 140)
    if t < 2.45:
        expanded = t >= 1.05
        p, (dx, dy, dw, dh) = draw_protect(img, t + 1.0, expanded)
        if not expanded:
            # "Ayrıntılar" düğmesinin yeri: gövdenin hemen altı
            body_n = len(wrap(PROTECT_BODY, dp(14), 400, dw - dp(48)))
            ay = dy + dp(24) + dp(44) + dp(30) + body_n * dp(20) + dp(6) + dp(26)
            touch(img, dx + dp(24) + dp(40), ay, t, 0.8)
        else:
            bx, by = p["Yine de yükle"]
            yx, yy = p["_yy"]
            highlight(img, dx + dp(14), yy, dw - dp(28), dp(44), t, 1.2, 1.0)
            touch(img, bx, by, t, 2.2)
        return img.convert("RGB")
    ip = prog(t, 2.55, 1.3)
    done = ip >= 1
    msg = "Uygulama yüklendi." if done else "Yükleniyor…"
    dlg, pos = install_dialog(t, msg, ("Bitti", "Aç") if done else None, progress=None if done else ease_in_out(ip))
    p = place_dialog(img, dlg, pos, 1.0)
    if done:
        touch(img, *p["Aç"], t, 4.55)
    # Dakk açılıyor
    if t >= 4.85:
        k = min(int((t - 4.85) * 26), 29)
        sp = splash_frame(k).copy()
        sp.putalpha(int(255 * ease_out(prog(t, 4.85, 0.3))))
        img.alpha_composite(sp)
    return img.convert("RGB")


# ---------------------------------------------------------------- sihirbaz parçaları (SetupWizard.kt)

WIZ_STEPS = 9
STEP_BG = (38, 33, 12)  # sarı %12, koyu zemin üstünde


def wizard_top(img, index, back=True, skip=True):
    y = dp(26)
    if back:
        put(img, rich("Geri", dp(14), 500, TXT2), dp(16), y + dp(14))
    if skip:
        s = rich("Atla", dp(14), 600, TXT2)
        put(img, s, SW - dp(16) - s.width, y + dp(14))
    d = ImageDraw.Draw(img)
    widths = [dp(20) if i == index else dp(7) for i in range(WIZ_STEPS)]
    total = sum(widths) + dp(6) * (WIZ_STEPS - 1)
    x = (SW - total) / 2
    for i, wd in enumerate(widths):
        col = Y if i == index else ((128, 106, 13) if i < index else BORDER)
        d.rounded_rectangle((x, y + dp(22), x + wd, y + dp(29)), dp(3.5), fill=col)
        x += wd + dp(6)


def step_frame(img, primary, secondary=None, press=0.0):
    bw, bh = SW - dp(48), dp(56)
    s = 1 - 0.04 * press
    b = rrect(int(bw * s), int(bh * s), dp(16), Y).copy()
    lab = rich(primary, dp(16), 700, (0, 0, 0))
    b.alpha_composite(lab, ((b.width - lab.width) // 2, (b.height - lab.height) // 2))
    by = SH - dp(24) - bh - (dp(44) if secondary else 0)
    put(img, b, dp(24) + (bw - b.width) / 2, by + (bh - b.height) / 2)
    if secondary:
        st = rich(secondary, dp(14), 500, TXT2)
        put(img, st, (SW - st.width) / 2, by + bh + dp(12))
    return (SW / 2, by + bh / 2)


def step_body(img, emoji_cp, title, text, y_center=None, emoji_draw=None):
    lines = wrap(text, dp(16), 400, SW - dp(56))
    h = dp(104) + dp(24) + dp(34) + dp(12) + len(lines) * dp(23)
    y = (y_center or SH * 0.43) - h / 2
    c = circle(dp(104), STEP_BG)
    put(img, c, (SW - c.width) / 2, y)
    if emoji_draw:
        emoji_draw(img, (SW - dp(104)) / 2, y)
    else:
        e = emoji_img(emoji_cp, dp(52))
        put(img, e, (SW - e.width) / 2, y + (dp(104) - e.height) / 2)
    y += dp(128)
    tt = rich(title, dp(26), 900, WHITE)
    put(img, tt, (SW - tt.width) / 2, y)
    y += dp(46)
    for line in lines:
        lt = rich(line, dp(16), 400, TXT2)
        put(img, lt, (SW - lt.width) / 2, y)
        y += dp(23)
    return y


def wiz_base():
    img = base()
    status_bar(img, BG)
    return img


def _welcome(t, press_at):
    img = wiz_base()
    wizard_top(img, 0, back=False, skip=False)
    y = SH * 0.30
    ring = circle(dp(126), Y)
    put(img, ring, (SW - ring.width) / 2, y - dp(3))
    put(img, circle(dp(120), WHITE), (SW - dp(120)) / 2, y)
    lg = logo(dp(112))
    put(img, lg, (SW - lg.width) / 2, y + dp(4))
    y += dp(148)
    tt = rich("Dakk'a hoş geldin 👋", dp(26), 900, WHITE)
    put(img, tt, (SW - tt.width) / 2, y)
    y += dp(46)
    for line in wrap("Hızlı, sade ve uçtan uca şifreli mesajlaşma. Birkaç kısa adımda hazırız.", dp(16), 400, SW - dp(56)):
        lt = rich(line, dp(16), 400, TXT2)
        put(img, lt, (SW - lt.width) / 2, y)
        y += dp(23)
    press = math.sin(prog(t, press_at, 0.22) * math.pi)
    c = step_frame(img, "Başlayalım", press=press)
    touch(img, *c, t, press_at)
    return img


def _account_sheet(img, t, t_in, tap_at):
    sp = min(ease_out(prog(t, t_in, 0.35)), 1 - ease_in_out(prog(t, tap_at + 0.3, 0.3)))
    if sp <= 0:
        return
    dim(img, 130 * sp)
    sh_h = dp(330)
    sy = SH - sh_h * sp
    put(img, rrect(SW, sh_h + dp(30), dp(28), (32, 33, 36), corners=(True, True, False, False)), 0, sy)
    y = sy + dp(26)
    t1 = rich("Hesap seçin", dp(20), 500, WHITE)
    put(img, t1, (SW - t1.width) / 2, y)
    y += dp(32)
    t2 = rich("Dakk uygulamasına devam etmek için", dp(13), 400, (189, 193, 198))
    put(img, t2, (SW - t2.width) / 2, y)
    y += dp(40)
    rh = dp(64)
    hp = prog(t, tap_at - 0.05, 0.45)
    if 0 < hp < 1:
        put(img, rrect(SW - dp(32), rh, dp(12), (60, 64, 67)), dp(16), y, 1 - hp)
    put(img, avatar(dp(40), (0, 168, 132), "A"), dp(28), y + dp(12))
    put(img, rich("Ayşe Yılmaz", dp(15), 600, WHITE), dp(82), y + dp(12))
    put(img, rich("ayse.yilmaz@gmail.com", dp(13), 400, (189, 193, 198)), dp(82), y + dp(34))
    touch(img, SW / 2, y + rh / 2, t, tap_at)
    y += rh
    ImageDraw.Draw(img).rectangle((dp(16), y + dp(6), SW - dp(16), y + dp(7)), fill=(60, 64, 67))
    y += dp(20)
    d = ImageDraw.Draw(img)
    d.ellipse((dp(36), y + dp(8), dp(60), y + dp(32)), outline=(189, 193, 198), width=dp(2))
    d.line((dp(48), y + dp(14), dp(48), y + dp(26)), fill=(189, 193, 198), width=dp(2))
    d.line((dp(42), y + dp(20), dp(54), y + dp(20)), fill=(189, 193, 198), width=dp(2))
    put(img, rich("Başka bir hesap ekle", dp(15), 500, WHITE), dp(82), y + dp(8))


# ---------------------------------------------------------------- 6) Dakk'ı aç, Google ile gir

def scene_open_signin(t):
    t = max(t, 0.0)
    if t < 1.0:
        k = min(int((t + 1.15) * 26), 29)
        sp = splash_frame(k)
        img = sp.copy()
        if t > 0.75:
            w = _welcome(t, 99)
            w.putalpha(int(255 * prog(t, 0.75, 0.25)))
            img.alpha_composite(w)
        return img.convert("RGB")
    if t < 2.35:
        return _welcome(t, 1.95).convert("RGB")
    img, (bx, by) = login_screen()
    if t < 2.65:
        w = _welcome(t, 1.95)
        w.putalpha(int(255 * (1 - prog(t, 2.35, 0.3))))
        img.alpha_composite(w)
    loading = t >= 4.9
    if loading:
        # düğmede "Giriş yapılıyor…"
        b = rrect(SW - dp(40), dp(54), dp(16), Y).copy()
        lab = rich("Giriş yapılıyor…", dp(16), 600, (0, 0, 0))
        b.alpha_composite(lab, ((b.width - lab.width) // 2 + dp(14), (b.height - lab.height) // 2))
        d = ImageDraw.Draw(b)
        cx, cy, r = (b.width - lab.width) // 2 - dp(4), b.height // 2, dp(10)
        a0 = (t * 400) % 360
        d.arc((cx - r, cy - r, cx + r, cy + r), a0, a0 + 270, fill=(0, 0, 0), width=dp(2.5))
        put(img, b, dp(20), by - dp(27))
    press = math.sin(prog(t, 3.15, 0.22) * math.pi)
    if press > 0:
        dim_btn = rrect(SW - dp(40), dp(54), dp(16), (0, 0, 0, int(40 * press)))
        put(img, dim_btn, dp(20), by - dp(27))
    touch(img, bx, by, t, 3.15)
    _account_sheet(img, t, 3.45, 4.55)
    if t >= 4.85:
        # sayfa iner
        pass
    return img.convert("RGB")


# ---------------------------------------------------------------- 7) ad + bildirim izni + yedek

def _profile(t, press_at):
    img = wiz_base()
    wizard_top(img, 3)
    y = step_body(img, 0x1F642, "Seni nasıl görelim?", "Arkadaşların seni bu adla görür. Sonra Ayarlar'dan değiştirebilirsin.",
                  y_center=SH * 0.36)
    y += dp(24)
    fw = SW - dp(56)
    put(img, rrect(fw, dp(58), dp(6), (0, 0, 0, 0), outline=Y + (255,), width=dp(2)), dp(28), y)
    put(img, rrect(dp(92), dp(18), 0, BG), dp(40), y - dp(9))
    put(img, rich("Görünen ad", dp(12), 500, Y), dp(46), y - dp(10))
    put(img, rich("Ayşe Yılmaz", dp(16), 400, WHITE), dp(44), y + dp(16))
    press = math.sin(prog(t, press_at, 0.22) * math.pi)
    c = step_frame(img, "Kaydet ve devam et", press=press)
    touch(img, *c, t, press_at)
    return img


def _notif(t, press_at, sys_in, allow_at):
    img = wiz_base()
    wizard_top(img, 4)
    y = step_body(img, 0x1F514, "Mesajlar kaçmasın",
                  "Arkadaşların yazınca haber verelim mi? Bildirimdeki mesaj yalnızca senin telefonunda çözülür.",
                  y_center=SH * 0.36)
    y += dp(18)
    info = wrap("Birazdan Android küçük bir izin penceresi açacak. \"İzin ver\"e dokunman yeterli.",
                dp(13), 400, SW - dp(56) - dp(60))
    card = rrect(SW - dp(56), dp(26) + len(info) * dp(19), dp(14), SURF).copy()
    card.alpha_composite(emoji_img(0x1F4A1, dp(20)), (dp(14), (card.height - dp(20)) // 2))
    for i, line in enumerate(info):
        card.alpha_composite(rich(line, dp(13), 400, TXT2), (dp(46), dp(13) + i * dp(19)))
    put(img, card, dp(28), y)
    press = math.sin(prog(t, press_at, 0.22) * math.pi)
    c = step_frame(img, "Bildirimlere izin ver", "Şimdi değil", press=press)
    touch(img, *c, t, press_at)
    # Android 13+ bildirim izni penceresi
    a = min(ease_out(prog(t, sys_in, 0.3)), 1 - ease_in_out(prog(t, allow_at + 0.25, 0.25)))
    if a > 0:
        dim(img, 150 * a)
        w = SW - dp(56)
        lines = wrap("Dakk uygulamasının sana bildirim göndermesine izin verilsin mi?", dp(18), 400, w - dp(48))
        h = dp(84) + len(lines) * dp(25) + dp(132)
        dlg = rrect(w, h, dp(28), DLG).copy()
        dd = ImageDraw.Draw(dlg)
        # zil simgesi
        bx, by = w // 2, dp(38)
        dd.pieslice((bx - dp(11), by - dp(12), bx + dp(11), by + dp(12)), 180, 360, fill=BLUE)
        dd.rectangle((bx - dp(11), by, bx + dp(11), by + dp(8)), fill=BLUE)
        dd.rectangle((bx - dp(14), by + dp(8), bx + dp(14), by + dp(10)), fill=BLUE)
        dd.ellipse((bx - dp(3.5), by + dp(11), bx + dp(3.5), by + dp(17)), fill=BLUE)
        y2 = dp(70)
        for line in lines:
            lt = rich(line, dp(18), 400, WHITE)
            dlg.alpha_composite(lt, ((w - lt.width) // 2, y2))
            y2 += dp(25)
        y2 += dp(18)
        pos = {}
        for k_, lab in enumerate(("İzin ver", "İzin verme")):
            hp = prog(t, allow_at - 0.05, 0.4) if k_ == 0 else 0
            col = (82, 90, 110) if 0 < hp < 1 else (58, 62, 70)
            dlg.alpha_composite(rrect(w - dp(48), dp(46), dp(23), col), (dp(24), y2))
            lt = rich(lab, dp(15), 600, BLUE)
            dlg.alpha_composite(lt, ((w - lt.width) // 2, y2 + (dp(46) - lt.height) // 2))
            pos[lab] = (w / 2, y2 + dp(23))
            y2 += dp(54)
        p = place_dialog(img, dlg, pos, a)
        touch(img, *p["İzin ver"], t, allow_at)
    return img


def backup_switch(img, x, y, on=True):
    w, h = dp(52), dp(32)
    put(img, rrect(w, h, h // 2, Y if on else SURFV), x, y)
    r = dp(12)
    cx = x + (w - dp(16) if on else dp(16))
    put(img, circle(2 * r, (0, 0, 0) if on else TXT2), cx - r, y + h / 2 - r)


def _backup(t, press_at):
    img = wiz_base()
    wizard_top(img, 5)
    y = step_body(img, 0x1F49B, "Sohbet yedeği",
                  "Telefonunu değiştirirsen arkadaşlarının telefonları ortak sohbetlerinizi sana şifreli olarak geri gönderir.",
                  y_center=SH * 0.36)
    y += dp(20)
    card = rrect(SW - dp(56), dp(70), dp(16), SURF).copy()
    card.alpha_composite(rich("Sohbet yedeği", dp(16), 600, WHITE), (dp(16), dp(13)))
    card.alpha_composite(rich("Açık · önerilir", dp(13), 400, TXT2), (dp(16), dp(38)))
    put(img, card, dp(28), y)
    backup_switch(img, SW - dp(28) - dp(16) - dp(52), y + dp(19), True)
    press = math.sin(prog(t, press_at, 0.22) * math.pi)
    c = step_frame(img, "Devam", press=press)
    touch(img, *c, t, press_at)
    return img


def scene_setup(t):
    t = max(t, 0.0)
    p1 = ease_in_out(prog(t, 1.05, 0.35))
    p2 = ease_in_out(prog(t, 4.35, 0.35))
    if p1 < 1:
        a = _profile(t, 0.75)
        if p1 <= 0:
            return a.convert("RGB")
        return slide_between(a, _notif(t, 2.3, 2.6, 3.75), p1).convert("RGB")
    if p2 <= 0:
        return _notif(t, 2.3, 2.6, 3.75).convert("RGB")
    b = _backup(t, 5.45)
    if p2 < 1:
        return slide_between(_notif(t, 2.3, 2.6, 3.75), b, p2).convert("RGB")
    return b.convert("RGB")


# ---------------------------------------------------------------- 8) hazırsın → ana ekran

def _done(t, press_at):
    img = wiz_base()
    wizard_top(img, 8, skip=False)

    def party(im, x, y):
        k = back_out(prog(t, 0.05, 0.5))
        e = emoji_img(0x1F389, max(2, int(dp(52) * min(k, 1.15))))
        put(im, e, x + (dp(104) - e.width) / 2, y + (dp(104) - e.height) / 2)

    step_body(img, None, "Hazırsın!",
              "Şimdi sana 30 saniyelik kısa bir tur gösterelim. İstersen Ayarlar'dan tekrar izleyebilirsin.",
              emoji_draw=party)
    # konfeti
    import random
    rnd = random.Random(11)
    lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    cp = prog(t, 0.1, 2.2)
    if 0 < cp < 1:
        cols = [Y, (255, 107, 0), (100, 181, 246), (129, 199, 132), (240, 98, 146), WHITE]
        for _ in range(70):
            x0 = SW / 2 + rnd.uniform(-dp(30), dp(30))
            y0 = SH * 0.33
            vx = rnd.uniform(-dp(190), dp(190))
            vy = rnd.uniform(-dp(320), -dp(80))
            tt = cp * 2.2
            x = x0 + vx * tt
            y = y0 + vy * tt + dp(260) * tt * tt
            s = dp(rnd.uniform(3, 6))
            a = int(255 * (1 - cp))
            ang = rnd.random() * math.pi + tt * 6
            pts = [(x + math.cos(ang + k * math.pi / 2) * s, y + math.sin(ang + k * math.pi / 2) * s * 0.5) for k in range(4)]
            d.polygon(pts, fill=rnd.choice(cols) + (a,))
        img.alpha_composite(lay)
    press = math.sin(prog(t, press_at, 0.22) * math.pi)
    c = step_frame(img, "Dakk'ı aç", press=press)
    touch(img, *c, t, press_at)
    return img


def _home_empty(t, tap_at):
    img = base()
    d = ImageDraw.Draw(img)
    y = dp(26) + dp(14)
    put(img, rich("Dakk", dp(26), 700, Y), dp(20), y)
    y += dp(48)
    put(img, rrect(SW - dp(32), dp(44), dp(22), SURFV), dp(16), y)
    put(img, rich("Sohbetlerde ara", dp(15), 400, TXT3), dp(36), y + dp(11))
    y += dp(60)
    # Dakk Asistan satırı
    put(img, circle(dp(50), (38, 35, 58)), dp(16), y + dp(12))
    e = emoji_img(0x2728, dp(26))
    put(img, e, dp(16) + (dp(50) - e.width) / 2, y + dp(12) + (dp(50) - e.height) / 2)
    put(img, rich("Dakk Asistan", dp(16), 600, WHITE), dp(80), y + dp(16))
    put(img, rich("Bir şey sor, yardımcı olayım ✨", dp(13), 400, TXT2), dp(80), y + dp(40))
    y += dp(86)
    d.rectangle((0, y, SW, y + 1), fill=BORDER)
    # boş durum
    cy = SH * 0.55
    put(img, circle(dp(96), STEP_BG), (SW - dp(96)) / 2, cy - dp(120))
    e = emoji_img(0x1F4AC, dp(46))
    put(img, e, (SW - e.width) / 2, cy - dp(120) + (dp(96) - e.height) / 2)
    tt = rich("Henüz sohbet yok", dp(18), 600, WHITE)
    put(img, tt, (SW - tt.width) / 2, cy)
    for i, line in enumerate(wrap("Arkadaşını QR kodla ya da davet bağlantısıyla ekle.", dp(14), 400, SW - dp(80))):
        lt = rich(line, dp(14), 400, TXT2)
        put(img, lt, (SW - lt.width) / 2, cy + dp(32) + i * dp(20))
    press = math.sin(prog(t, tap_at, 0.22) * math.pi)
    s = 1 - 0.05 * press
    bw, bh = int(dp(150) * s), int(dp(48) * s)
    b = rrect(bw, bh, bh // 2, Y).copy()
    lab = rich("Kişi ekle", dp(15), 700, (0, 0, 0))
    b.alpha_composite(lab, ((bw - lab.width) // 2, (bh - lab.height) // 2))
    by = cy + dp(88)
    put(img, b, (SW - bw) / 2, by + (dp(48) - bh) / 2)
    touch(img, SW / 2, by + dp(24), t, tap_at)
    # alt gezinme
    nav_h = dp(72)
    ny = SH - nav_h
    d = ImageDraw.Draw(img)
    d.rectangle((0, ny, SW, SH), fill=SURF)
    for i, lab in enumerate(["Sohbetler", "Kişiler", "Ayarlar"]):
        cx = SW * (i + 0.5) / 3
        sel = i == 0
        if sel:
            put(img, rrect(dp(60), dp(30), dp(15), (61, 52, 16)), cx - dp(30), ny + dp(10))
        lt = rich(lab, dp(12), 600 if sel else 400, WHITE if sel else TXT2)
        put(img, lt, cx - lt.width / 2, ny + dp(44))
        col = Y if sel else TXT2
        d.rounded_rectangle((cx - dp(9), ny + dp(17), cx + dp(9), ny + dp(31)), dp(4), outline=col, width=dp(2))
    status_bar(img, BG)
    # tanıtım turu ipucu balonu
    tp = ease_out(prog(t, tap_at - 1.1, 0.35))
    if tp > 0:
        tip = rrect(dp(220), dp(48), dp(14), Y).copy()
        tl = rich("Buradan ilk arkadaşını ekle", dp(13), 700, (0, 0, 0))
        tip.alpha_composite(tl, ((tip.width - tl.width) // 2, (tip.height - tl.height) // 2))
        tx, ty = (SW - tip.width) / 2, by + dp(66) - (1 - tp) * dp(10)
        put(img, tip, tx, ty, tp)
        lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
        ImageDraw.Draw(lay).polygon([(SW / 2 - dp(9), ty + dp(1)), (SW / 2 + dp(9), ty + dp(1)), (SW / 2, ty - dp(9))],
                                    fill=Y + (int(255 * tp),))
        img.alpha_composite(lay)
    return img


def scene_ready(t):
    t = max(t, 0.0)
    p = ease_in_out(prog(t, 2.55, 0.4))
    if p <= 0:
        return _done(t, 2.25).convert("RGB")
    h = _home_empty(t, 4.9)
    if p >= 1:
        return h.convert("RGB")
    a = _done(t, 2.25)
    h2 = h.copy()
    h2.putalpha(int(255 * p))
    a.alpha_composite(h2)
    return a.convert("RGB")


SCENE_FUNCS.update({
    "i5_tarama": scene_install_scan,
    "i6_protect": scene_protect,
    "i7_giris": scene_open_signin,
    "i8_sihirbaz": scene_setup,
    "i9_hazir": scene_ready,
})


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/install_sheet.png"
    times = [float(x) for x in sys.argv[2].split(",")] if len(sys.argv) > 2 else [0.8, 1.8, 2.6, 3.3, 4.1, 4.8]
    names = sys.argv[3].split(",") if len(sys.argv) > 3 else list(SCENE_FUNCS)
    sheet = Image.new("RGB", (len(times) * 320, len(names) * 700), (90, 90, 90))
    for r, fn in enumerate(SCENE_FUNCS[n] for n in names):
        for c, tt in enumerate(times):
            sheet.paste(fn(tt).resize((310, 689)), (c * 320, r * 700))
    sheet.save(out)
    print(out)
