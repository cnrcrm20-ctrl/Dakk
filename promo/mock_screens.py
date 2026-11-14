"""
Dakk ekranlarının sadeleştirilmiş, animasyonlu çizimleri (Google reklamlarındaki gibi).

Renkler ve şekiller uygulamadan alınmıştır (ui/theme/Color.kt, ChatScreen.kt, PollComponents.kt):
koyu zemin, sarı vurgu, benim balonum 0xFF3D3410, karşı taraf 0xFF1E2028, köşeler 18dp / kuyruk 4dp.
Her sahne fonksiyonu sahnenin yerel zamanını (saniye) alır ve ekran boyutunda bir RGB görüntü döndürür.
"""
import math
import os
from functools import lru_cache

import qrcode
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_PATH = os.path.join(HERE, "assets", "fonts", "GoogleSans.ttf")
EMOJI_DIR = os.path.join(HERE, "assets", "emoji")
PHOTO = os.path.join(HERE, "assets", "halisaha.jpg")

SW, SH = 620, 1378
D = 2.0  # 1 dp = 2 px (310 dp genişliğinde, okunaklı ve sade bir telefon)

BG = (9, 10, 13)
SURF = (18, 19, 24)
SURFV = (27, 29, 36)
BORDER = (39, 42, 53)
WHITE = (255, 255, 255)
TXT2 = (160, 165, 181)
TXT3 = (107, 114, 128)
Y = (255, 204, 0)
MINE = (61, 52, 16)
THEIRS = (30, 32, 40)
AVATAR = [(255, 138, 0), (124, 77, 255), (0, 168, 132), (30, 136, 229),
          (233, 30, 99), (67, 160, 71), (244, 81, 30), (94, 53, 177)]
SENDER = [(255, 183, 77), (129, 199, 132), (100, 181, 246), (240, 98, 146)]


def dp(x):
    return int(round(x * D))


def clamp01(p):
    return min(max(p, 0.0), 1.0)


def ease_out(p):
    p = clamp01(p)
    return 1 - (1 - p) ** 3


def ease_in_out(p):
    p = clamp01(p)
    return 4 * p ** 3 if p < 0.5 else 1 - (-2 * p + 2) ** 3 / 2


def back_out(p, s=1.7):
    p = clamp01(p)
    p -= 1
    return 1 + (s + 1) * p ** 3 + s * p ** 2


def prog(t, start, dur):
    return clamp01((t - start) / dur)


# ---------------------------------------------------------------- yazı ve emoji

_fonts = {}


def font(size, weight=400):
    key = (size, weight)
    if key not in _fonts:
        f = ImageFont.truetype(FONT_PATH, size)
        f.set_variation_by_axes([18, weight, 0])
        _fonts[key] = f
    return _fonts[key]


_EMOJI_SET = {int(f[:-4], 16) for f in os.listdir(EMOJI_DIR) if f.endswith(".png")}


def is_emoji(ch):
    return ord(ch) in _EMOJI_SET


@lru_cache(maxsize=64)
def emoji_img(cp, size):
    im = Image.open(os.path.join(EMOJI_DIR, f"{cp:x}.png")).convert("RGBA")
    return im.resize((size, size), Image.LANCZOS)


def runs(text):
    out, buf = [], ""
    for ch in text:
        if ord(ch) in (0xFE0F, 0x200D):
            continue
        if is_emoji(ch):
            if buf:
                out.append(("t", buf))
                buf = ""
            out.append(("e", ch))
        else:
            buf += ch
    if buf:
        out.append(("t", buf))
    return out


def rich_width(text, size, weight=400):
    f = font(size, weight)
    w = 0.0
    for kind, s in runs(text):
        w += f.getlength(s) if kind == "t" else size * 1.18
    return w


@lru_cache(maxsize=512)
def rich(text, size, weight=400, color=WHITE):
    """Tek satır yazı (emoji destekli) → RGBA."""
    f = font(size, weight)
    asc, desc = f.getmetrics()
    w = int(math.ceil(rich_width(text, size, weight))) + 4
    h = asc + desc + 4
    img = Image.new("RGBA", (max(w, 1), h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x = 0.0
    for kind, s in runs(text):
        if kind == "t":
            d.text((x, 2), s, font=f, fill=color + (255,))
            x += f.getlength(s)
        else:
            e = int(size * 1.05)
            em = emoji_img(ord(s), e)
            img.alpha_composite(em, (int(x + size * 0.06), int(2 + asc - e * 0.86)))
            x += size * 1.18
    return img


def wrap(text, size, weight, max_w):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for word in para.split(" "):
            cand = word if not cur else cur + " " + word
            if cur and rich_width(cand, size, weight) > max_w:
                lines.append(cur)
                cur = word
            else:
                cur = cand
        lines.append(cur)
    return lines


def put(canvas, img, x, y, op=1.0):
    if op <= 0.001:
        return
    if op < 0.999:
        img = img.copy()
        img.putalpha(img.getchannel("A").point(lambda v: int(v * op)))
    canvas.alpha_composite(img, (int(round(x)), int(round(y))))


# ---------------------------------------------------------------- şekiller

@lru_cache(maxsize=256)
def rrect(w, h, r, fill, corners=(True, True, True, True), outline=None, width=0, ss=3):
    """Kenarları yumuşatılmış yuvarlak dikdörtgen → RGBA (önbellekli)."""
    w, h = max(int(w), 1), max(int(h), 1)
    big = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.rounded_rectangle((0, 0, w * ss - 1, h * ss - 1), r * ss, fill=fill + (255,) if len(fill) == 3 else fill,
                        corners=corners, outline=outline, width=width * ss)
    return big.resize((w, h), Image.LANCZOS)


@lru_cache(maxsize=64)
def circle(size, fill):
    return rrect(size, size, size // 2, fill)


@lru_cache(maxsize=32)
def avatar(size, color, letter):
    img = circle(size, color).copy()
    if letter:
        t = rich(letter, int(size * 0.42), 600, WHITE)
        img.alpha_composite(t, ((size - t.width) // 2, (size - t.height) // 2 - 1))
    return img


def icon_back(d, x, y, color=WHITE):
    s = dp(9)
    d.line((x + s, y - s, x, y, x + s, y + s), fill=color, width=dp(2), joint="curve")
    d.line((x, y, x + dp(18), y), fill=color, width=dp(2))


def icon_lock(img, x, y, size, color):
    ss = 4
    big = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    S = size * ss
    d.rounded_rectangle((S * 0.16, S * 0.44, S * 0.84, S * 0.98), S * 0.12, fill=color + (255,))
    d.arc((S * 0.28, S * 0.06, S * 0.72, S * 0.66), 180, 360, fill=color + (255,), width=int(S * 0.12))
    d.line((S * 0.28 + S * 0.06, S * 0.36, S * 0.28 + S * 0.06, S * 0.46), fill=color + (255,), width=int(S * 0.12))
    d.line((S * 0.72 - S * 0.06, S * 0.36, S * 0.72 - S * 0.06, S * 0.46), fill=color + (255,), width=int(S * 0.12))
    img.alpha_composite(big.resize((size, size), Image.LANCZOS), (int(x), int(y)))


def icon_checks(img, x, y, double, color):
    ss = 4
    w, h = dp(18), dp(12)
    big = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    lw = int(dp(1.7) * ss)
    pts = lambda ox: [(ox + 0.5 * dp(1) * ss, 0.55 * h * ss), (ox + dp(4) * ss, 0.9 * h * ss), (ox + dp(10.5) * ss, 0.12 * h * ss)]
    if double:
        d.line(pts(0), fill=color + (255,), width=lw, joint="curve")
        d.line(pts(dp(5) * ss), fill=color + (255,), width=lw, joint="curve")
    else:
        d.line(pts(dp(3) * ss), fill=color + (255,), width=lw, joint="curve")
    img.alpha_composite(big.resize((w, h), Image.LANCZOS), (int(x), int(y)))


def icon_timer(img, x, y, size, color, angle=None):
    ss = 4
    S = size * ss
    big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    lw = max(2, int(S * 0.11))
    d.ellipse((lw, lw * 2, S - lw, S), outline=color + (255,), width=lw)
    d.line((S * 0.38, lw * 0.6, S * 0.62, lw * 0.6), fill=color + (255,), width=lw)
    cx, cy = S / 2, (lw * 2 + S) / 2
    a = -math.pi / 2 if angle is None else angle
    r = (S - lw * 3) / 2 * 0.62
    d.line((cx, cy, cx + r * math.cos(a), cy + r * math.sin(a)), fill=color + (255,), width=lw)
    img.alpha_composite(big.resize((size, size), Image.LANCZOS), (int(x), int(y)))


def icon_send(img, x, y, size, color):
    ss = 4
    S = size * ss
    big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.polygon([(S * 0.14, S * 0.16), (S * 0.9, S * 0.5), (S * 0.14, S * 0.84), (S * 0.24, S * 0.5)], fill=color + (255,))
    img.alpha_composite(big.resize((size, size), Image.LANCZOS), (int(x), int(y)))


# ---------------------------------------------------------------- ortak parçalar

STATUS_H = dp(26)
TOPBAR_H = dp(60)
INPUT_H = dp(64)


def status_bar(img, color=SURF):
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, SW, STATUS_H), fill=color)
    put(img, rich("21:00", dp(12), 600, WHITE), dp(18), dp(5))
    # pil ve sinyal
    x = SW - dp(44)
    d.rounded_rectangle((x, dp(9), x + dp(22), dp(19)), dp(3), outline=WHITE, width=2)
    d.rectangle((x + 3, dp(9) + 3, x + dp(18), dp(19) - 3), fill=WHITE)
    d.rectangle((x + dp(23), dp(12), x + dp(24.5), dp(16)), fill=WHITE)
    for i in range(4):
        hh = dp(3 + i * 2.2)
        d.rectangle((x - dp(26) + i * dp(5), dp(19) - hh, x - dp(23) + i * dp(5), dp(19)), fill=WHITE)


def top_bar(img, title, avatar_color=None, letter=None, subtitle="Uçtan uca şifreli", pulse=0.0):
    d = ImageDraw.Draw(img)
    y0 = STATUS_H
    d.rectangle((0, y0, SW, y0 + TOPBAR_H), fill=SURF)
    icon_back(d, dp(16), y0 + TOPBAR_H // 2)
    x = dp(48)
    if avatar_color:
        a = avatar(dp(38), avatar_color, letter)
        put(img, a, x, y0 + (TOPBAR_H - a.height) // 2)
        x += dp(48)
    t = rich(title, dp(16), 600, WHITE)
    if subtitle:
        put(img, t, x, y0 + dp(8))
        if pulse > 0:
            glow = rrect(dp(126), dp(20), dp(10), Y + (int(60 * pulse),))
            put(img, glow, x - dp(6), y0 + dp(32))
        icon_lock(img, x, y0 + dp(35), dp(11), Y)
        put(img, rich(subtitle, dp(12), 500, Y), x + dp(15), y0 + dp(31))
    else:
        put(img, t, x, y0 + (TOPBAR_H - t.height) // 2)


def input_bar(img, typed="", cursor=False, send_press=0.0):
    d = ImageDraw.Draw(img)
    y0 = SH - INPUT_H
    d.rectangle((0, y0, SW, SH), fill=BG)
    pill_w = SW - dp(8) - dp(8) - dp(48) - dp(8)
    put(img, rrect(pill_w, dp(48), dp(24), SURFV), dp(8), y0 + dp(8))
    if typed:
        # yazı alanı: uzun metin soldan kayar (gerçek giriş kutusu gibi)
        fw_, fh_ = pill_w - dp(28), dp(48)
        field = Image.new("RGBA", (fw_, fh_), (0, 0, 0, 0))
        if typed.startswith("@dakk"):
            parts = [("@dakk", 600, Y), (typed[5:], 400, WHITE)]
        else:
            parts = [(typed, 400, WHITE)]
        total = sum(rich_width(tx_, dp(15), wt) for tx_, wt, _ in parts) + dp(4)
        x = min(0, fw_ - total - dp(4))
        for tx_, wt, col in parts:
            if tx_:
                r_ = rich(tx_, dp(15), wt, col)
                field.alpha_composite(r_, (int(x), (fh_ - r_.height) // 2)) if x >= 0 else None
                if x < 0:
                    tmp = Image.new("RGBA", (fw_, fh_), (0, 0, 0, 0))
                    tmp.paste(r_, (int(x), (fh_ - r_.height) // 2))
                    field.alpha_composite(tmp)
            x += rich_width(tx_, dp(15), wt)
        if cursor:
            ImageDraw.Draw(field).rectangle((x + 3, dp(12), x + 3 + dp(1.5), dp(36)), fill=Y)
        put(img, field, dp(24), y0 + dp(8))
    else:
        t = rich("Mesaj", dp(15), 400, TXT3)
        put(img, t, dp(24), y0 + dp(8) + (dp(48) - t.height) // 2)
    enabled = bool(typed)
    s = dp(48) * (1 - 0.1 * send_press)
    btn = circle(int(s), Y if enabled else SURFV)
    bx = SW - dp(8) - dp(48) + (dp(48) - btn.width) / 2
    by = y0 + dp(8) + (dp(48) - btn.height) / 2
    put(img, btn, bx, by)
    icon_send(img, bx + btn.width * 0.27, by + btn.height * 0.25, int(btn.width * 0.5), (17, 17, 17) if enabled else TXT3)


@lru_cache(maxsize=64)
def bubble(text, mine, time="21:00", status=None, sender=None, sender_color=None, max_w=None, extra=None,
           bg=None, hl=None, timer=False):
    """Mesaj balonu → RGBA. extra='typing' üç nokta için, ('photo',) fotoğraf için."""
    max_w = max_w or dp(232)
    pad_x, pad_y = dp(12), dp(8)
    lines = wrap(text, dp(15), 400, max_w - 2 * pad_x) if text else []
    line_h = dp(21)
    meta = rich(time, dp(11), 400, TXT3)
    meta_w = meta.width + (dp(22) if mine else 0) + (dp(16) if timer else 0)
    content_w = max([rich_width(l, dp(15)) for l in lines] + [meta_w, dp(56)])
    head = rich(sender, dp(12), 600, sender_color) if sender else None
    if head:
        content_w = max(content_w, head.width)
    w = int(content_w + 2 * pad_x)
    h = pad_y + (dp(17) if head else 0) + len(lines) * line_h + dp(16) + pad_y
    corners = (True, True, not mine, mine)  # tl, tr, br, bl  (kuyruk köşesi küçük)
    fill = bg or (MINE if mine else THEIRS)
    shape = rrect(w, h, dp(18), fill, corners=(True, True, True, True))
    img = shape.copy()
    # kuyruk köşesi: 4dp
    tail = rrect(dp(18), dp(18), dp(4), fill)
    img.alpha_composite(tail, (w - dp(18) if mine else 0, h - dp(18)))
    y = pad_y
    if head:
        img.alpha_composite(head, (pad_x - 2, y - 2))
        y += dp(17)
    for li, l in enumerate(lines):
        if hl and li == 0 and l.startswith(hl):
            img.alpha_composite(rich(hl, dp(15), 600, Y), (pad_x - 2, y - 2))
            rest = l[len(hl):]
            if rest:
                img.alpha_composite(rich(rest, dp(15), 400, WHITE), (pad_x - 2 + int(rich_width(hl, dp(15), 600)), y - 2))
        else:
            img.alpha_composite(rich(l, dp(15), 400, WHITE), (pad_x - 2, y - 2))
        y += line_h
    mx = w - pad_x - meta_w
    if timer:
        icon_timer(img, mx, y + dp(2), dp(12), TXT3)
        mx += dp(16)
    img.alpha_composite(meta, (mx, y))
    if mine and status:
        icon_checks(img, mx + meta.width + dp(3), y + dp(3), status != "sent", Y if status == "read" else TXT3)
    return img


@lru_cache(maxsize=8)
def photo_bubble(w=dp(214), h=dp(250), mine=False, status=None, time="21:04"):
    pad = dp(4)
    photo = Image.open(PHOTO).convert("RGB")
    pw, ph = w - 2 * pad, h - 2 * pad - dp(18)
    r = max(pw / photo.width, ph / photo.height)
    photo = photo.resize((int(photo.width * r) + 1, int(photo.height * r) + 1), Image.LANCZOS)
    left, top = (photo.width - pw) // 2, int((photo.height - ph) * 0.55)
    photo = photo.crop((left, top, left + pw, top + ph)).convert("RGBA")
    mask = rrect(pw, ph, dp(14), (255, 255, 255)).getchannel("A")
    fill = MINE if mine else THEIRS
    img = rrect(w, h, dp(18), fill).copy()
    img.alpha_composite(rrect(dp(18), dp(18), dp(4), fill), (w - dp(18) if mine else 0, h - dp(18)))
    img.paste(photo, (pad, pad), mask)
    meta = rich(time, dp(11), 400, TXT3)
    extra = dp(22) if (mine and status) else 0
    mx = w - pad - dp(6) - meta.width - extra
    img.alpha_composite(meta, (mx, h - dp(19)))
    if mine and status:
        icon_checks(img, mx + meta.width + dp(3), h - dp(16), status != "sent", Y if status == "read" else TXT3)
    return img


def typing_bubble(t):
    w, h = dp(64), dp(36)
    img = rrect(w, h, dp(18), THEIRS).copy()
    img.alpha_composite(rrect(dp(18), dp(18), dp(4), THEIRS), (0, h - dp(18)))
    d = ImageDraw.Draw(img)
    for i in range(3):
        ph = (t * 2.4 - i * 0.18) % 1.0
        up = math.sin(ph * math.pi) if ph < 0.5 else 0
        c = int(110 + 120 * up)
        cx, cy = dp(18) + i * dp(14), h // 2 - up * dp(4)
        r = dp(3.6)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(c, c, c + 8))
    return img


class Stack:
    """Mesajları alttan yukarı dizer; yeni gelen mesaj kayarak girer, eskileri yumuşakça yukarı iter."""

    def __init__(self, bottom):
        self.bottom = bottom
        self.items = []

    def add(self, img, mine, p, gap=dp(6), extra_below=None, op=1.0, key=None):
        self.items.append((img, mine, p, gap, extra_below, op, key))

    def draw(self, canvas, top_clip):
        y = self.bottom
        placed = []
        self.rects = {}
        for img, mine, p, gap, extra, op, key in reversed(self.items):
            e = ease_out(p)
            if e <= 0:
                continue
            extra_h = extra[1] if extra else 0
            h = (img.height + extra_h + gap) * e
            y -= h
            placed.append((img, mine, p, y, extra, op, key))
        layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        for img, mine, p, y, extra, op, key in placed:
            e = ease_out(p)
            if mine is None:
                x = (SW - img.width) / 2
            else:
                x = SW - dp(10) - img.width if mine else dp(10)
            yy = y + (1 - e) * dp(18)
            if key:
                self.rects[key] = (x, yy, img.width, img.height)
            put(layer, img, x, yy, e * op)
            if extra:
                extra[0](layer, x, yy + img.height, img.width, e)
        layer = layer.crop((0, top_clip, SW, SH))
        canvas.alpha_composite(layer, (0, top_clip))


def base():
    return Image.new("RGBA", (SW, SH), BG + (255,))


def notice_pill(img, y, op=1.0):
    text = "Mesajların uçtan uca şifreli. Dakk bile okuyamaz."
    lines = wrap(text, dp(12), 500, dp(230))
    w = dp(256)
    h = dp(14) + len(lines) * dp(17)
    pill = rrect(w, h, dp(12), (40, 36, 16)).copy()
    yy = dp(7)
    for l in lines:
        t = rich(l, dp(12), 500, Y)
        pill.alpha_composite(t, ((w - t.width) // 2, yy - 2))
        yy += dp(17)
    icon = Image.new("RGBA", (dp(14), dp(14)), (0, 0, 0, 0))
    put(img, pill, (SW - w) / 2, y, op)


# ---------------------------------------------------------------- 1) sohbet

def scene_chat(t):
    t = max(t, 0.0)
    img = base()
    typed_full = "Akşam halı saha var mı? ⚽"
    t_type0, t_type1, t_send = 0.15, 1.3, 1.45
    n = int(len(typed_full) * clamp01((t - t_type0) / (t_type1 - t_type0)))
    typed = typed_full[:n] if t < t_send else ""
    status = "read" if t >= 2.05 else "sent"

    stack = Stack(SH - INPUT_H - dp(6))
    stack.add(bubble("Selam! 👋", False, "20:54"), False, 1.0)
    stack.add(bubble("Selaam, işten yeni çıktım 😎", True, "20:55", "read"), True, 1.0)
    stack.add(bubble("Bu akşam boş musun?", False, "20:58"), False, 1.0)
    stack.add(bubble(typed_full, True, "21:00", status), True, prog(t, t_send, 0.35))
    typing_p = min(prog(t, 2.25, 0.3), 1 - prog(t, 3.0, 0.15))
    stack.add(typing_bubble(t), False, typing_p)
    stack.add(bubble("Varım! 🔥", False, "21:00"), False, prog(t, 3.05, 0.35))
    notice_pill(img, STATUS_H + TOPBAR_H + dp(16))
    stack.draw(img, STATUS_H + TOPBAR_H)

    press = 1 - abs(prog(t, t_send - 0.1, 0.2) * 2 - 1) if t_send - 0.1 <= t <= t_send + 0.1 else 0
    input_bar(img, typed, cursor=(t < t_send and (t * 2.2) % 1 < 0.6), send_press=press)
    pulse = math.sin(prog(t, 0.0, 1.2) * math.pi)
    top_bar(img, "Ayşe", AVATAR[4], "A", pulse=pulse)
    status_bar(img)
    return img.convert("RGB")


# ---------------------------------------------------------------- 2) QR ile ekle

@lru_cache(maxsize=1)
def qr_card(size=dp(176)):
    q = qrcode.QRCode(border=1, box_size=10, error_correction=qrcode.constants.ERROR_CORRECT_M)
    q.add_data("https://cnrcrm20-ctrl.github.io/Dakk/invite/#mert")
    q.make(fit=True)
    im = q.make_image(fill_color="black", back_color="white").convert("RGB").resize((size, size), Image.NEAREST)
    card = rrect(size + dp(20), size + dp(20), dp(14), WHITE).copy()
    card.paste(im, (dp(10), dp(10)))
    return card


def scene_qr(t):
    t = max(t, 0.0)
    img = base()
    y0 = STATUS_H + TOPBAR_H
    d = ImageDraw.Draw(img)
    # sekmeler
    tabs_y = y0 + dp(14)
    put(img, rrect(dp(130), dp(36), dp(18), (48, 42, 14)), dp(24), tabs_y)
    t1 = rich("QR okut", dp(14), 600, Y)
    put(img, t1, dp(24) + (dp(130) - t1.width) / 2, tabs_y + (dp(36) - t1.height) / 2)
    put(img, rrect(dp(130), dp(36), dp(18), SURFV), SW - dp(24) - dp(130), tabs_y)
    t2 = rich("QR kodum", dp(14), 500, TXT2)
    put(img, t2, SW - dp(24) - dp(130) + (dp(130) - t2.width) / 2, tabs_y + (dp(36) - t2.height) / 2)

    # vizör
    vs = dp(262)
    vx, vy = (SW - vs) // 2, tabs_y + dp(62)
    cam = Image.new("RGBA", (vs, vs), (0, 0, 0, 0))
    grad = Image.linear_gradient("L").resize((vs, vs)).point(lambda v: 38 + v // 9)
    cam_rgb = Image.merge("RGBA", (grad, grad, grad.point(lambda v: v + 6), Image.new("L", (vs, vs), 255)))
    cam.alpha_composite(cam_rgb)
    card = qr_card()
    found = prog(t, 1.35, 0.25)
    sc = 1.0 + 0.04 * math.sin(found * math.pi)
    c2 = card.resize((int(card.width * sc), int(card.height * sc)), Image.BILINEAR)
    wob = math.sin(t * 2.0) * dp(3) * (1 - found)
    cam.alpha_composite(c2, ((vs - c2.width) // 2 + int(wob), (vs - c2.height) // 2))
    mask = rrect(vs, vs, dp(22), WHITE).getchannel("A")
    cam.putalpha(Image.composite(cam.getchannel("A"), Image.new("L", (vs, vs), 0), mask))
    img.alpha_composite(cam, (vx, vy))
    # tarama çizgisi
    if t < 1.4:
        sp = (t / 1.2) % 1.0
        ly = vy + dp(22) + (vs - dp(44)) * (0.5 - 0.5 * math.cos(sp * math.pi * 2))
        glow = rrect(vs - dp(40), dp(14), dp(7), Y + (70,))
        put(img, glow, vx + dp(20), ly - dp(6))
        d.rectangle((vx + dp(22), ly, vx + vs - dp(22), ly + dp(2)), fill=Y)
    # köşe çerçeveleri
    L, wdt = dp(34), dp(4)
    col = Y if found < 1 else (255, 224, 102)
    grow = dp(6) * math.sin(found * math.pi)
    for cx, cy, sx, sy in ((vx, vy, 1, 1), (vx + vs, vy, -1, 1), (vx, vy + vs, 1, -1), (vx + vs, vy + vs, -1, -1)):
        cx -= sx * (dp(8) + grow)
        cy -= sy * (dp(8) + grow)
        d.line((cx, cy + sy * L, cx, cy, cx + sx * L, cy), fill=col, width=wdt, joint="curve")
    hint = rich("Arkadaşının QR kodunu çerçeveye getir", dp(13), 400, TXT2)
    put(img, hint, (SW - hint.width) / 2, vy + vs + dp(26), 1 - found)

    # alt sayfa
    sp = ease_out(prog(t, 1.5, 0.45))
    if sp > 0:
        sh_h = dp(250)
        sy = SH - sh_h * sp
        dim = Image.new("RGBA", (SW, SH), (0, 0, 0, int(110 * sp)))
        img.alpha_composite(dim)
        sheet = rrect(SW, sh_h + dp(30), dp(26), SURF, corners=(True, True, False, False))
        put(img, sheet, 0, sy)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((SW / 2 - dp(18), sy + dp(10), SW / 2 + dp(18), sy + dp(14)), dp(2), fill=BORDER)
        av = avatar(dp(56), AVATAR[3], "M")
        put(img, av, dp(24), sy + dp(32))
        put(img, rich("Mert Kaya", dp(19), 600, WHITE), dp(94), sy + dp(36))
        icon_lock(img, dp(94), sy + dp(66), dp(11), Y)
        put(img, rich("Dakk'ta · uçtan uca şifreli", dp(13), 400, TXT2), dp(110), sy + dp(62))
        # düğme
        press = math.sin(prog(t, 2.35, 0.25) * math.pi)
        done = t >= 2.55
        bw, bh = SW - dp(48), dp(52)
        s = 1 - 0.04 * press
        btn = rrect(int(bw * s), int(bh * s), dp(14), (SURFV if done else Y))
        bx, by = (SW - btn.width) / 2, sy + dp(110) + (bh - btn.height) / 2
        put(img, btn, bx, by)
        label = rich("Kişilere ekle", dp(16), 600, (20, 20, 20))
        if done:
            label = rich("Eklendi", dp(16), 600, Y)
            pop = back_out(prog(t, 2.55, 0.35))
            icon_checks(img, SW / 2 - label.width / 2 - dp(14), by + btn.height / 2 - dp(6), False, Y)
            put(img, label, SW / 2 - label.width / 2 + dp(10), by + (btn.height - label.height) / 2, min(pop, 1))
        else:
            put(img, label, (SW - label.width) / 2, by + (btn.height - label.height) / 2)
        sub = rich("Artık şifreli sohbet edebilirsiniz.", dp(13), 400, TXT3)
        put(img, sub, (SW - sub.width) / 2, sy + dp(176), prog(t, 2.6, 0.3))

    top_bar(img, "Kişi ekle", subtitle=None)
    status_bar(img)
    return img.convert("RGB")


# ---------------------------------------------------------------- 3) anket + takım kur

def poll_card(t):
    """Maç anketi balonu (PollComponents.kt'nin sade hali)."""
    w = dp(262)
    pad = dp(12)
    votes_at = {"Geliyorum": [0.15, 0.4, 0.62, 0.82, 1.0, 1.18], "Belki": [0.55], "Gelmiyorum": [0.92]}
    my_vote_t = 0.4
    counts = {k: sum(1 for x in v if t >= x) for k, v in votes_at.items()}
    total = max(1, sum(counts.values()))
    coming = counts["Geliyorum"]
    h = dp(318)
    img = rrect(w, h, dp(18), THEIRS).copy()
    img.alpha_composite(rrect(dp(18), dp(18), dp(4), THEIRS), (0, h - dp(18)))
    y = dp(8)
    img.alpha_composite(rich("Emre", dp(12), 600, SENDER[0]), (pad - 2, y - 2))
    y += dp(19)
    img.alpha_composite(emoji_img(0x26BD, dp(13)), (pad, y + dp(1)))
    img.alpha_composite(rich("MAÇ ANKETİ", dp(11), 700, Y), (pad + dp(17), y - 2))
    y += dp(19)
    img.alpha_composite(rich("Maça geliyor musun? ⚽", dp(16), 600, WHITE), (pad - 2, y - 2))
    y += dp(22)
    img.alpha_composite(rich("Bir seçenek seç", dp(12), 400, TXT3), (pad - 2, y - 2))
    y += dp(24)
    d = ImageDraw.Draw(img)
    ow = w - 2 * pad
    for opt in ("Geliyorum", "Belki", "Gelmiyorum"):
        chosen = opt == "Geliyorum" and t >= my_vote_t
        img.alpha_composite(rrect(ow, dp(40), dp(12), (16, 17, 21)), (pad, y))
        frac = counts[opt] / total
        # oran çubuğu yumuşak büyür
        last = max([x for x in votes_at[opt] if t >= x], default=None)
        if last is not None:
            frac_prev = (counts[opt] - 1) / max(1, total)
            frac = frac_prev + (frac - frac_prev) * ease_out((t - last) / 0.35)
        fw = int(ow * frac)
        if fw > dp(6):
            col = (88, 75, 22) if chosen else (44, 46, 54)
            img.alpha_composite(rrect(fw, dp(40), dp(12), col), (pad, y))
        rx, ry, rr = pad + dp(12), y + dp(20), dp(8)
        if chosen:
            pop = back_out(prog(t, my_vote_t, 0.3))
            r2 = rr * min(pop, 1.15)
            d.ellipse((rx - r2 + rr, ry - r2, rx + r2 + rr, ry + r2), fill=Y)
            icon_checks(img, rx + rr - dp(7), ry - dp(5), False, (20, 20, 20))
        else:
            d.ellipse((rx, ry - rr, rx + 2 * rr, ry + rr), outline=TXT3, width=dp(1.5))
        img.alpha_composite(rich(opt, dp(14), 400, WHITE), (pad + dp(34), y + dp(9)))
        c = rich(str(counts[opt]), dp(13), 600, WHITE if counts[opt] else TXT3)
        img.alpha_composite(c, (pad + ow - dp(12) - c.width, y + dp(10)))
        y += dp(46)
    # takım kur düğmesi
    press = math.sin(prog(t, 1.62, 0.22) * math.pi)
    s = 1 - 0.05 * press
    bw, bh = int(ow * s), int(dp(42) * s)
    active = coming >= 2
    btn = rrect(bw, bh, dp(12), Y if active else SURFV)
    bx, by = pad + (ow - bw) // 2, y + dp(4) + (dp(42) - bh) // 2
    img.alpha_composite(btn, (bx, by))
    label = f"Takım kur ({coming} kişi)" if active else "Takım kur (en az 2 kişi)"
    lt = rich(label, dp(14), 600, (20, 20, 20) if active else TXT3)
    img.alpha_composite(lt, (bx + (bw - lt.width) // 2, by + (bh - lt.height) // 2))
    meta = rich("21:02", dp(11), 400, TXT3)
    img.alpha_composite(meta, (w - pad - meta.width, h - dp(20)))
    return img


@lru_cache(maxsize=2)
def teams_bubble():
    w = dp(236)
    pad = dp(12)
    h = dp(150)
    img = rrect(w, h, dp(18), MINE).copy()
    img.alpha_composite(rrect(dp(18), dp(18), dp(4), MINE), (w - dp(18), h - dp(18)))
    y = dp(9)
    img.alpha_composite(rich("⚽ Takımlar hazır!", dp(15), 600, WHITE), (pad - 2, y - 2))
    y += dp(30)
    img.alpha_composite(rich("🟡 Takım 1 (3)", dp(13), 600, Y), (pad - 2, y - 2))
    y += dp(19)
    img.alpha_composite(rich("Sen · Emre · Burak", dp(14), 400, WHITE), (pad - 2, y - 2))
    y += dp(25)
    img.alpha_composite(rich("⚫ Takım 2 (3)", dp(13), 600, TXT2), (pad - 2, y - 2))
    y += dp(19)
    img.alpha_composite(rich("Mert · Ali · Kerem", dp(14), 400, WHITE), (pad - 2, y - 2))
    meta = rich("21:03", dp(11), 400, TXT3)
    img.alpha_composite(meta, (w - pad - meta.width - dp(22), h - dp(20)))
    icon_checks(img, w - pad - dp(19), h - dp(17), True, Y)
    return img


def scene_poll(t):
    t = max(t, 0.0)
    img = base()
    stack = Stack(SH - INPUT_H - dp(6))
    stack.add(bubble("Perşembe 21:00, sahayı ayarladım 👌", False, "21:01", sender="Burak", sender_color=SENDER[2]), False, 1.0)
    stack.add(poll_card(t), False, 1.0)
    stack.add(teams_bubble(), True, prog(t, 1.9, 0.45), gap=dp(8))
    stack.draw(img, STATUS_H + TOPBAR_H)
    input_bar(img)
    top_bar(img, "Halı Saha ⚽", AVATAR[5], "H", subtitle="8 üye · uçtan uca şifreli")
    status_bar(img)
    return img.convert("RGB")


# ---------------------------------------------------------------- 4) tepki + fotoğraf

REACTIONS = (0x1F602, 0x2764, 0x2B50, 0x1F44C)


def scene_react(t):
    t = max(t, 0.0)
    img = base()
    t_press, t_pick, t_close = 0.45, 1.35, 1.65
    photo = photo_bubble()
    hold = math.sin(prog(t, t_press, 0.3) * math.pi) if t < t_press + 0.3 else 0
    chip_p = back_out(prog(t, t_close + 0.05, 0.4))

    def chip(layer, x, y, w, op):
        if chip_p <= 0:
            return
        cw, ch = dp(46), dp(28)
        s = min(chip_p, 1.2)
        c = rrect(int(cw * s), int(ch * s), dp(12), (58, 50, 18)).copy()
        e = emoji_img(0x2764, int(dp(15) * s))
        c.alpha_composite(e, (int(dp(8) * s), (c.height - e.height) // 2))
        one = rich("1", int(dp(12) * s) or 1, 600, WHITE)
        c.alpha_composite(one, (int(dp(28) * s), (c.height - one.height) // 2))
        put(layer, c, x + dp(8), y - dp(6), op * clamp01(chip_p * 2))

    stack = Stack(SH - INPUT_H - dp(6))
    pimg = photo
    if hold > 0:
        s = 1 - 0.03 * hold
        pimg = photo.resize((int(photo.width * s), int(photo.height * s)), Image.BILINEAR)
    stack.add(pimg, False, 1.0, extra_below=(chip, dp(24) * clamp01(chip_p)))
    stack.add(bubble("Saha harika! Geliyorum 😎", True, "21:05", "read"), True, 1.0)
    typing_p = min(prog(t, 2.2, 0.3), 1 - prog(t, 2.95, 0.15))
    stack.add(typing_bubble(t), False, typing_p)
    stack.add(bubble("Kaleci benim 🧤", False, "21:06"), False, prog(t, 3.0, 0.35))
    stack.draw(img, STATUS_H + TOPBAR_H)
    input_bar(img)
    top_bar(img, "Mert", AVATAR[3], "M")
    status_bar(img)

    # uzun basma: karartma + tepki çubuğu
    menu = min(prog(t, t_press + 0.15, 0.25), 1 - prog(t, t_close, 0.2))
    if menu > 0:
        dim = Image.new("RGBA", (SW, SH), (0, 0, 0, int(140 * menu)))
        img.alpha_composite(dim)
        # fotoğrafın konumunu hesapla (yığının ilk öğesi)
        total = 0
        for it in stack.items[1:]:
            e = ease_out(it[2])
            total += (it[0].height + it[3]) * e
        py = SH - INPUT_H - dp(6) - total - photo.height - dp(6) - dp(24) * clamp01(chip_p)
        put(img, photo, dp(10), py)
        bw, bh = dp(212), dp(56)
        bar = rrect(bw, bh, dp(28), SURFV)
        bx, by = dp(10), py - bh - dp(10)
        s = back_out(prog(t, t_press + 0.15, 0.35))
        bar_s = bar.resize((max(1, int(bw * min(s, 1.1))), max(1, int(bh * min(s, 1.1)))), Image.BILINEAR)
        put(img, bar_s, bx, by + (bh - bar_s.height) / 2, menu)
        for i, cp in enumerate(REACTIONS):
            ep = back_out(prog(t, t_press + 0.22 + i * 0.05, 0.35))
            size = dp(32) * min(ep, 1.15)
            picked = cp == 0x2764 and t >= t_pick
            if picked:
                k = math.sin(prog(t, t_pick, 0.3) * math.pi)
                size *= 1 + 0.35 * k
                ring = circle(dp(46), (70, 60, 20))
                put(img, ring, bx + dp(10) + i * dp(50) - dp(7), by + dp(5), menu)
            if size >= 2:
                e = emoji_img(cp, int(size))
                cx = bx + dp(10) + i * dp(50) + dp(16)
                put(img, e, cx - e.width / 2, by + bh / 2 - e.height / 2, menu)
    return img.convert("RGB")


# ---------------------------------------------------------------- 5) sohbet yedeği

CHATS = [
    ("Ayşe", AVATAR[4], "A", "Varım! 🔥", "21:00"),
    ("Halı Saha ⚽", AVATAR[5], "H", "⚽ Takımlar hazır!", "21:03"),
    ("Mert", AVATAR[3], "M", "Kaleci benim 🧤", "21:06"),
    ("Annem", AVATAR[0], "A", "Yemek hazır, gel ❤️", "19:40"),
    ("Burak", AVATAR[1], "B", "Perşembe 21:00 👌", "Dün"),
]


def scene_restore(t):
    t = max(t, 0.0)
    img = base()
    d = ImageDraw.Draw(img)
    y = STATUS_H + dp(14)
    put(img, rich("Dakk", dp(26), 700, Y), dp(20), y)
    y += dp(48)
    put(img, rrect(SW - dp(32), dp(44), dp(22), SURFV), dp(16), y)
    put(img, rich("Sohbetlerde ara", dp(15), 400, TXT3), dp(36), y + dp(11))
    y += dp(58)

    # geri yükleme kartı
    done = prog(t, 2.55, 0.3)
    card_h = dp(76)
    put(img, rrect(SW - dp(32), card_h, dp(16), (40, 36, 16)), dp(16), y)
    title = "Sohbet geçmişin geri geldi" if done > 0 else "Sohbet geçmişin geri geliyor…"
    put(img, rich(title, dp(15), 600, WHITE), dp(32), y + dp(12))
    sub = "3 arkadaşın şifreli olarak gönderdi" if done > 0 else "Arkadaşlarının telefonlarından, şifreli"
    put(img, rich(sub, dp(12), 400, TXT2), dp(32), y + dp(33))
    pw = SW - dp(64)
    pr = ease_in_out(prog(t, 0.3, 2.3))
    put(img, rrect(pw, dp(6), dp(3), (70, 62, 26)), dp(32), y + dp(58))
    if pr > 0.02:
        put(img, rrect(max(dp(6), int(pw * pr)), dp(6), dp(3), Y), dp(32), y + dp(58))
    if done > 0:
        pop = back_out(done)
        c = circle(max(2, int(dp(26) * min(pop, 1.1))), Y)
        put(img, c, SW - dp(32) - dp(26) + (dp(26) - c.width) / 2, y + dp(12) + (dp(26) - c.height) / 2)
        icon_checks(img, SW - dp(32) - dp(26) + dp(4), y + dp(19), False, (20, 20, 20))
    y += card_h + dp(12)

    # sohbet satırları
    row_h = dp(74)
    for i, (name, col, letter, last, tm) in enumerate(CHATS):
        p = ease_out(prog(t, 0.55 + i * 0.33, 0.45))
        if p <= 0:
            continue
        x_off = (1 - p) * dp(60)
        row = Image.new("RGBA", (SW, row_h), (0, 0, 0, 0))
        row.alpha_composite(avatar(dp(50), col, letter), (dp(16), (row_h - dp(50)) // 2))
        row.alpha_composite(rich(name, dp(16), 600, WHITE), (dp(80), dp(14)))
        row.alpha_composite(rich(last, dp(14), 400, TXT2), (dp(80), dp(40)))
        tt = rich(tm, dp(12), 400, TXT3)
        row.alpha_composite(tt, (SW - dp(16) - tt.width, dp(16)))
        # küçük "geri yüklendi" işareti
        mark = rrect(dp(20), dp(20), dp(10), (48, 42, 14))
        row.alpha_composite(mark, (SW - dp(16) - dp(20), dp(40)))
        icon_checks(row, SW - dp(16) - dp(20) + dp(1), dp(44), False, Y)
        put(img, row, x_off, y + i * row_h, p)

    # alt gezinme çubuğu
    nav_h = dp(72)
    ny = SH - nav_h
    d = ImageDraw.Draw(img)
    d.rectangle((0, ny, SW, SH), fill=SURF)
    labels = ["Sohbetler", "Kişiler", "Ayarlar"]
    for i, lab in enumerate(labels):
        cx = SW * (i + 0.5) / 3
        sel = i == 0
        if sel:
            put(img, rrect(dp(60), dp(30), dp(15), (61, 52, 16)), cx - dp(30), ny + dp(10))
        col = Y if sel else TXT2
        if i == 0:
            d.rounded_rectangle((cx - dp(10), ny + dp(17), cx + dp(10), ny + dp(32)), dp(4), outline=col, width=dp(2))
        elif i == 1:
            d.ellipse((cx - dp(5), ny + dp(14), cx + dp(5), ny + dp(24)), outline=col, width=dp(2))
            d.arc((cx - dp(10), ny + dp(25), cx + dp(10), ny + dp(42)), 180, 360, fill=col, width=dp(2))
        else:
            d.ellipse((cx - dp(9), ny + dp(16), cx + dp(9), ny + dp(34)), outline=col, width=dp(2))
            d.ellipse((cx - dp(3), ny + dp(22), cx + dp(3), ny + dp(28)), fill=col)
        lt = rich(lab, dp(12), 600 if sel else 400, WHITE if sel else TXT2)
        put(img, lt, cx - lt.width / 2, ny + dp(44))
    # yeni sohbet düğmesi
    fab = rrect(dp(56), dp(56), dp(16), Y)
    fx, fy = SW - dp(16) - dp(56), ny - dp(16) - dp(56)
    put(img, fab, fx, fy)
    d.line((fx + dp(28), fy + dp(18), fx + dp(28), fy + dp(38)), fill=(20, 20, 20), width=dp(2.5))
    d.line((fx + dp(18), fy + dp(28), fx + dp(38), fy + dp(28)), fill=(20, 20, 20), width=dp(2.5))
    status_bar(img, BG)
    return img.convert("RGB")


# ================================================================ uzun (69 sn) sürüm için ek sahneler

def sys_pill(text, color=TXT2, bg=SURFV, size=12):
    t = rich(text, dp(size), 500, color)
    w, h = t.width + dp(24), t.height + dp(10)
    img = rrect(w, h, h // 2, bg).copy()
    img.alpha_composite(t, ((w - t.width) // 2, (h - t.height) // 2))
    return img


def slide_between(a, b, p):
    """Android tarzı geçiş: yeni ekran sağdan gelir, eskisi biraz sola kayıp kararır."""
    if p <= 0:
        return a
    if p >= 1:
        return b
    out = base()
    out.alpha_composite(a, (int(-SW * 0.28 * p), 0))
    out.alpha_composite(Image.new("RGBA", (SW, SH), (0, 0, 0, int(150 * p))))
    sh = Image.new("RGBA", (dp(16), SH), (0, 0, 0, 0))
    for i in range(dp(16)):
        ImageDraw.Draw(sh).line((i, 0, i, SH), fill=(0, 0, 0, int(70 * (i / dp(16)))))
    x = int(SW * (1 - p))
    out.alpha_composite(sh, (max(0, x - dp(16)), 0))
    out.alpha_composite(b, (x, 0))
    return out


def tap_ripple(img, x, y, p, r_max=dp(34)):
    if 0 < p < 1:
        r = r_max * ease_out(p)
        a = int(90 * (1 - p))
        ring = circle(max(2, int(2 * r)), WHITE + (a,)) if False else None
        lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
        ImageDraw.Draw(lay).ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, a))
        img.alpha_composite(lay)


def plain_top_bar(img, title, sub=None, sub_color=TXT2):
    d = ImageDraw.Draw(img)
    y0 = STATUS_H
    d.rectangle((0, y0, SW, y0 + TOPBAR_H), fill=SURF)
    icon_back(d, dp(16), y0 + TOPBAR_H // 2)
    t = rich(title, dp(17), 600, WHITE)
    if sub:
        put(img, t, dp(52), y0 + dp(9))
        put(img, rich(sub, dp(12), 500, sub_color), dp(52), y0 + dp(33))
    else:
        put(img, t, dp(52), y0 + (TOPBAR_H - t.height) // 2)


def check_circle(img, x, y, size, p):
    """Seçim dairesi: boş halka → sarı dolu + tik (p: 0..1)."""
    d = ImageDraw.Draw(img)
    if p <= 0:
        put(img, rrect(size, size, size // 2, (0, 0, 0, 0), outline=TXT3 + (255,), width=dp(1.5)), x, y)
        return
    s = min(back_out(p), 1.15)
    c = circle(max(2, int(size * s)), Y)
    put(img, c, x + (size - c.width) / 2, y + (size - c.height) / 2)
    if p > 0.4:
        icon_checks(img, x + size / 2 - dp(7), y + size / 2 - dp(5), False, (20, 20, 20))


# ---------------------------------------------------------------- 6) grup kur

GROUP_PICK = [("Emre", AVATAR[0], 1.15), ("Burak", AVATAR[1], 1.4), ("Mert", AVATAR[3], 1.65),
              ("Ali", AVATAR[6], 1.9), ("Kerem", AVATAR[7], 2.15), ("Ayşe", AVATAR[4], None), ("Can", AVATAR[2], None)]


def _group_create(t):
    img = base()
    y = STATUS_H + TOPBAR_H + dp(18)
    # grup fotoğrafı + ad alanı
    name_full = "Halı Saha ⚽"
    n = int(len(name_full) * clamp01((t - 0.15) / 0.8))
    name = name_full[:n]
    av = circle(dp(56), SURFV).copy()
    if n == len(name_full):
        pop = back_out(prog(t, 0.95, 0.3))
        e = emoji_img(0x26BD, max(2, int(dp(30) * min(pop, 1.1))))
        av.alpha_composite(e, ((av.width - e.width) // 2, (av.height - e.height) // 2))
    else:
        icon = rich("📷", dp(22), 400, WHITE)
        av.alpha_composite(icon, ((av.width - icon.width) // 2, (av.height - icon.height) // 2))
    put(img, av, dp(18), y)
    put(img, rich("Grup adı", dp(12), 600, Y), dp(88), y + dp(2))
    nm = rich(name if name else " ", dp(18), 500, WHITE)
    put(img, nm, dp(88), y + dp(20))
    d = ImageDraw.Draw(img)
    if t < 1.05 and (t * 2.2) % 1 < 0.6:
        cx = dp(88) + rich_width(name, dp(18), 500) + 3
        d.rectangle((cx, y + dp(24), cx + dp(1.5), y + dp(46)), fill=Y)
    d.rectangle((dp(88), y + dp(52), SW - dp(18), y + dp(53)), fill=Y if t < 1.1 else BORDER)
    y += dp(76)
    # seçilenler şeridi
    chosen = [(nm_, c, ts) for nm_, c, ts in GROUP_PICK if ts is not None and t >= ts]
    if not chosen:
        put(img, rich("Kişileri seç", dp(14), 400, TXT3), dp(18), y + dp(22))
    for i, (nm_, c, ts) in enumerate(chosen):
        pop = back_out(prog(t, ts, 0.35))
        a = avatar(dp(44), c, nm_[0])
        s = min(pop, 1.12)
        a2 = a.resize((max(2, int(a.width * s)), max(2, int(a.height * s))), Image.BILINEAR)
        cx = dp(18) + i * dp(58)
        put(img, a2, cx + (dp(44) - a2.width) / 2, y + (dp(44) - a2.height) / 2, clamp01(pop * 2))
        lt = rich(nm_, dp(11), 500, TXT2)
        put(img, lt, cx + dp(22) - lt.width / 2, y + dp(47), clamp01(pop))
    y += dp(72)
    d.rectangle((0, y, SW, y + 1), fill=BORDER)
    y += dp(12)
    put(img, rich("KİŞİLER", dp(11), 700, TXT3), dp(18), y)
    y += dp(24)
    row_h = dp(62)
    for i, (nm_, c, ts) in enumerate(GROUP_PICK):
        ry = y + i * row_h
        if ts is not None:
            hp = prog(t, ts - 0.05, 0.45)
            if 0 < hp < 1:
                put(img, rrect(SW, row_h, 0, SURFV), 0, ry, 1 - hp)
        put(img, avatar(dp(44), c, nm_[0]), dp(18), ry + (row_h - dp(44)) // 2)
        put(img, rich(nm_, dp(16), 500, WHITE), dp(74), ry + dp(10))
        put(img, rich("Dakk'ta", dp(12), 400, TXT3), dp(74), ry + dp(34))
        check_circle(img, SW - dp(18) - dp(24), ry + (row_h - dp(24)) // 2,
                     dp(24), prog(t, ts, 0.3) if ts is not None else 0)
    # üst çubuk + sayaç
    cnt = len(chosen)
    plain_top_bar(img, "Yeni grup", f"{cnt} kişi seçildi · en fazla 50" if cnt else "Üye ekle", Y if cnt else TXT2)
    # "Grubu kur" düğmesi
    fp = back_out(prog(t, 1.2, 0.4))
    if fp > 0:
        press = math.sin(prog(t, 3.05, 0.25) * math.pi)
        fw, fh = dp(150), dp(54)
        s = min(fp, 1.08) * (1 - 0.06 * press)
        fab = rrect(max(2, int(fw * s)), max(2, int(fh * s)), dp(16), Y).copy()
        lab = rich("Grubu kur", dp(15), 700, (20, 20, 20))
        if s > 0.6:
            icon_checks(fab, dp(16) * s, fab.height / 2 - dp(6), False, (20, 20, 20))
            fab.alpha_composite(lab, (int(fab.width / 2 - lab.width / 2 + dp(10) * s), (fab.height - lab.height) // 2))
        fx = SW - dp(18) - fw + (fw - fab.width) / 2
        fy = SH - dp(24) - fh + (fh - fab.height) / 2
        put(img, fab, fx, fy)
        tap_ripple(img, SW - dp(18) - fw / 2, SH - dp(24) - fh / 2, prog(t, 3.05, 0.4), dp(40))
    status_bar(img)
    return img


def _group_chat(t):
    img = base()
    stack = Stack(SH - INPUT_H - dp(6))
    stack.add(sys_pill("Bugün"), None, 1.0, gap=dp(14))
    stack.add(sys_pill("Grubu kurdun · 6 üye", Y, (40, 36, 16)), None, prog(t, 3.55, 0.3), gap=dp(14))
    stack.add(bubble("Hadi bakalım! 🔥", False, "21:01", sender="Emre", sender_color=SENDER[0]), False, prog(t, 4.05, 0.35))
    stack.add(bubble("Kaptan benim 😎", False, "21:01", sender="Burak", sender_color=SENDER[2]), False, prog(t, 4.55, 0.35))
    stack.add(bubble("👋👋", False, "21:01", sender="Mert", sender_color=SENDER[1]), False, prog(t, 5.0, 0.35))
    stack.draw(img, STATUS_H + TOPBAR_H)
    input_bar(img)
    top_bar(img, "Halı Saha ⚽", AVATAR[5], "H", subtitle="6 üye · uçtan uca şifreli")
    status_bar(img)
    return img


def scene_group(t):
    t = max(t, 0.0)
    p = ease_in_out(prog(t, 3.3, 0.45))
    a = _group_create(t) if p < 1 else None
    b = _group_chat(t) if p > 0 else None
    return (slide_between(a, b, p) if a is not None and b is not None else (a or b)).convert("RGB")


# ---------------------------------------------------------------- 7) şifreli fotoğraf

def scene_photo(t):
    t = max(t, 0.0)
    img = base()
    up = ease_in_out(prog(t, 0.45, 1.25))
    status = "read" if t >= 2.15 else ("sent" if up >= 1 else None)
    pb = photo_bubble(mine=True, status=status, time="21:04")
    stack = Stack(SH - INPUT_H - dp(6))
    stack.add(bubble("Sahayı gördün mü? 😍", False, "21:03"), False, 1.0)
    stack.add(bubble("Fotoğrafını at", False, "21:03"), False, 1.0)
    stack.add(pb, True, prog(t, 0.2, 0.4), key="photo")
    stack.add(sys_pill("🔒 Fotoğraf açıldı · sunucudaki kopyası silindi", Y, (40, 36, 16), 11), None,
              prog(t, 2.45, 0.35), gap=dp(12))
    stack.add(bubble("Işıklar süper! ⚽", False, "21:04"), False, prog(t, 2.95, 0.35))
    stack.draw(img, STATUS_H + TOPBAR_H)
    # yükleme halkası
    if "photo" in stack.rects and up < 1:
        x, y, w, h = stack.rects["photo"]
        ov = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
        od = ImageDraw.Draw(ov)
        od.rounded_rectangle((x + dp(4), y + dp(4), x + w - dp(4), y + h - dp(22)), dp(14), fill=(0, 0, 0, 110))
        cx, cy, r = x + w / 2, y + (h - dp(18)) / 2, dp(24)
        od.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(0, 0, 0, 150))
        od.arc((cx - r + dp(3), cy - r + dp(3), cx + r - dp(3), cy + r - dp(3)), -90, -90 + 360 * up,
               fill=Y + (255,), width=dp(3))
        img.alpha_composite(ov)
        icon_lock(img, cx - dp(8), cy - dp(9), dp(16), WHITE)
        lab = rich("Şifreleniyor…", dp(12), 600, WHITE)
        put(img, lab, cx - lab.width / 2, cy + r + dp(6))
    input_bar(img)
    top_bar(img, "Mert", AVATAR[3], "M")
    status_bar(img)

    # tam ekran görüntüleyici
    vp = ease_in_out(prog(t, 3.65, 0.45))
    if vp > 0 and "photo" in stack.rects:
        x, y, w, h = stack.rects["photo"]
        img.alpha_composite(Image.new("RGBA", (SW, SH), (0, 0, 0, int(255 * vp))))
        photo = Image.open(PHOTO).convert("RGB")
        fw = SW
        fh = int(photo.height * fw / photo.width)
        fy = (SH - fh) / 2
        sx, sy, sw, sh = x + dp(4), y + dp(4), w - dp(8), h - dp(26)
        cw, ch = sw + (fw - sw) * vp, sh + (fh - sh) * vp
        cx, cy = sx + (0 - sx) * vp, sy + (fy - sy) * vp
        zoom = 1 + 0.45 * ease_in_out(prog(t, 4.35, 1.35))
        target = photo.resize((max(2, int(cw * zoom)), max(2, int(ch * zoom))), Image.BILINEAR)
        # yakınlaştırma odağı: kale direkleri ve ışıklar
        ox = (target.width - cw) * 0.38
        oy = (target.height - ch) * 0.35
        crop = target.crop((int(ox), int(oy), int(ox + cw), int(oy + ch))).convert("RGBA")
        rad = int(dp(14) * (1 - vp))
        if rad > 0:
            crop.putalpha(rrect(crop.width, crop.height, rad, WHITE).getchannel("A"))
        img.alpha_composite(crop, (int(cx), int(cy)))
        # görüntüleyici üst çubuğu
        bar = Image.new("RGBA", (SW, STATUS_H + TOPBAR_H), (0, 0, 0, 150))
        bd = ImageDraw.Draw(bar)
        icon_back(bd, dp(16), STATUS_H + TOPBAR_H // 2)
        bar.alpha_composite(rich("Fotoğraf", dp(16), 600, WHITE), (dp(52), STATUS_H + dp(9)))
        bar.alpha_composite(rich("Uçtan uca şifreli", dp(12), 500, Y), (dp(52), STATUS_H + dp(33)))
        # indir simgesi
        ix, iy = SW - dp(40), STATUS_H + dp(18)
        bd.line((ix + dp(10), iy, ix + dp(10), iy + dp(16)), fill=WHITE, width=dp(2))
        bd.line((ix + dp(4), iy + dp(10), ix + dp(10), iy + dp(16), ix + dp(16), iy + dp(10)), fill=WHITE, width=dp(2))
        bd.line((ix + dp(2), iy + dp(22), ix + dp(18), iy + dp(22)), fill=WHITE, width=dp(2))
        put(img, bar, 0, 0, vp)
        status_bar(img, (0, 0, 0))
        hint = sys_pill("İki parmakla yakınlaştır · galeriye kaydet", WHITE, (40, 40, 44), 12)
        put(img, hint, (SW - hint.width) / 2, SH - dp(70), vp * (1 - prog(t, 5.5, 0.3)))
        # iki parmak
        fpp = prog(t, 4.25, 1.45)
        if 0 < fpp < 1:
            op = min(fpp * 5, (1 - fpp) * 5, 1)
            spread = dp(40) + dp(70) * ease_in_out(fpp)
            mx, my = SW * 0.42, SH * 0.48
            lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
            ld = ImageDraw.Draw(lay)
            for sgn in (-1, 1):
                px, py = mx + sgn * spread * 0.7, my + sgn * spread * 0.7
                ld.ellipse((px - dp(20), py - dp(20), px + dp(20), py + dp(20)), fill=(255, 255, 255, int(110 * op)),
                           outline=(255, 255, 255, int(200 * op)), width=dp(2))
            img.alpha_composite(lay)
    return img.convert("RGB")


# ---------------------------------------------------------------- 8) Dakk Asistan

ASSIST_BG = (38, 35, 58)
ASSIST_LABEL = (179, 157, 255)
ASSIST_TEXT = "Hatırlatma ⚽ Perşembe 21:00'de halı sahadayız! Su ve kramponunu unutma. Geç kalan kaleye geçer 😂"


def assist_typing(t):
    w, h = dp(64), dp(36)
    img = rrect(w, h, dp(18), ASSIST_BG).copy()
    img.alpha_composite(rrect(dp(18), dp(18), dp(4), ASSIST_BG), (0, h - dp(18)))
    d = ImageDraw.Draw(img)
    for i in range(3):
        ph = (t * 2.4 - i * 0.18) % 1.0
        up = math.sin(ph * math.pi) if ph < 0.5 else 0
        cx, cy = dp(18) + i * dp(14), h // 2 - up * dp(4)
        r = dp(3.6)
        c = tuple(int(v * (0.55 + 0.45 * up)) for v in ASSIST_LABEL)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c)
    return img


def scene_assistant(t):
    t = max(t, 0.0)
    img = base()
    typed_full = "@dakk perşembe için kısa bir hatırlatma yaz"
    t0, t1, t_send = 0.15, 1.55, 1.7
    n = int(len(typed_full) * clamp01((t - t0) / (t1 - t0)))
    typed = typed_full[:n] if t < t_send else ""
    stack = Stack(SH - INPUT_H - dp(6))
    stack.add(bubble("Perşembe 21:00, sahayı ayarladım 👌", False, "21:01", sender="Burak", sender_color=SENDER[2]), False, 1.0)
    stack.add(bubble("Kim ne getiriyor? 🤔", False, "21:02", sender="Emre", sender_color=SENDER[0]), False, 1.0)
    stack.add(bubble(typed_full, True, "21:02", "read" if t > 2.0 else "sent", hl="@dakk"), True, prog(t, t_send, 0.35))
    tp = min(prog(t, 2.05, 0.3), 1 - prog(t, 2.75, 0.12))
    stack.add(assist_typing(t), False, tp)
    sp = prog(t, 2.8, 1.5)
    if sp > 0:
        k = max(1, int(len(ASSIST_TEXT) * sp))
        txt = ASSIST_TEXT[:k]
        stack.add(bubble(txt, False, "21:02", sender="✨ Dakk Asistan", sender_color=ASSIST_LABEL, bg=ASSIST_BG),
                  False, prog(t, 2.8, 0.3))
    stack.add(bubble("Hahah tamam 😂", False, "21:03", sender="Emre", sender_color=SENDER[0]), False, prog(t, 4.75, 0.35))
    stack.draw(img, STATUS_H + TOPBAR_H)
    input_bar(img, typed, cursor=(t < t_send and (t * 2.2) % 1 < 0.6), send_press=0)
    # @dakk önerisi
    sg = min(prog(t, 0.3, 0.25), 1 - prog(t, 1.25, 0.2))
    if sg > 0:
        cw, ch = SW - dp(16), dp(62)
        card = rrect(cw, ch, dp(16), SURFV).copy()
        card.alpha_composite(circle(dp(40), ASSIST_BG), (dp(12), dp(11)))
        e = emoji_img(0x2728, dp(22))
        card.alpha_composite(e, (dp(12) + (dp(40) - e.width) // 2, dp(11) + (dp(40) - e.height) // 2))
        card.alpha_composite(rich("Dakk Asistan", dp(15), 600, WHITE), (dp(62), dp(10)))
        card.alpha_composite(rich("Sohbete yapay zekâ yanıtı ekler", dp(12), 400, TXT2), (dp(62), dp(34)))
        put(img, card, dp(8), SH - INPUT_H - ch - dp(4) + (1 - ease_out(sg)) * dp(14), sg)
    top_bar(img, "Halı Saha ⚽", AVATAR[5], "H", subtitle="6 üye · uçtan uca şifreli")
    status_bar(img)
    return img.convert("RGB")


# ---------------------------------------------------------------- 9) kaybolan mesajlar

INFO_ROWS = [(0x1F515, "Sessize al", "Kapalı"), (0x23F1, "Kaybolan mesajlar", None),
             (0x1F512, "Güvenlik numarası", "Doğrula"), (0x270F, "Takma ad", "Ayşe"), (0x1F6AB, "Engelle", "")]


def _info_screen(t):
    img = base()
    y = STATUS_H + TOPBAR_H + dp(22)
    a = avatar(dp(92), AVATAR[4], "A")
    put(img, a, (SW - a.width) / 2, y)
    y += dp(102)
    nm = rich("Ayşe", dp(22), 600, WHITE)
    put(img, nm, (SW - nm.width) / 2, y)
    y += dp(34)
    sub = rich("Uçtan uca şifreli", dp(13), 500, Y)
    sx = (SW - sub.width - dp(16)) / 2
    icon_lock(img, sx, y + dp(4), dp(12), Y)
    put(img, sub, sx + dp(16), y)
    y += dp(40)
    row_h = dp(58)
    card_h = row_h * len(INFO_ROWS)
    put(img, rrect(SW - dp(24), card_h, dp(18), SURF), dp(12), y)
    chosen = t >= 1.45
    for i, (cp, title, val) in enumerate(INFO_ROWS):
        ry = y + i * row_h
        if i == 1:
            hp = prog(t, 0.45, 0.5)
            if 0 < hp < 1:
                put(img, rrect(SW - dp(24), row_h, dp(10), SURFV), dp(12), ry, 1 - hp)
            val = "24 saat" if chosen else "Kapalı"
        put(img, emoji_img(cp, dp(20)), dp(28), ry + (row_h - dp(20)) // 2)
        col = (255, 107, 107) if title == "Engelle" else WHITE
        put(img, rich(title, dp(15), 500, col), dp(62), ry + (row_h - dp(22)) // 2)
        if val:
            vc = Y if (i == 1 and chosen) else TXT3
            vt = rich(val, dp(13), 600 if vc == Y else 400, vc)
            put(img, vt, SW - dp(28) - vt.width, ry + (row_h - vt.height) // 2)
        if i < len(INFO_ROWS) - 1:
            ImageDraw.Draw(img).rectangle((dp(62), ry + row_h - 1, SW - dp(12), ry + row_h), fill=BORDER)
    plain_top_bar(img, "Sohbet bilgisi")
    status_bar(img)
    # seçenek sayfası
    sp = min(ease_out(prog(t, 0.65, 0.4)), 1 - ease_in_out(prog(t, 1.85, 0.35)))
    if sp > 0:
        img.alpha_composite(Image.new("RGBA", (SW, SH), (0, 0, 0, int(130 * sp))))
        sh_h = dp(300)
        sy = SH - sh_h * sp
        put(img, rrect(SW, sh_h + dp(30), dp(26), SURF, corners=(True, True, False, False)), 0, sy)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((SW / 2 - dp(18), sy + dp(10), SW / 2 + dp(18), sy + dp(14)), dp(2), fill=BORDER)
        put(img, rich("Kaybolan mesajlar", dp(18), 600, WHITE), dp(24), sy + dp(28))
        for li, line in enumerate(wrap("Açıkken yeni mesajlar süre dolunca iki telefondan da silinir.", dp(13), 400, SW - dp(48))):
            put(img, rich(line, dp(13), 400, TXT2), dp(24), sy + dp(58) + li * dp(18))
        oy = sy + dp(108)
        for k, opt in enumerate(("Kapalı", "24 saat", "7 gün")):
            ry = oy + k * dp(52)
            on = (k == 1 and chosen) or (k == 0 and not chosen)
            if k == 1:
                hp = prog(t, 1.4, 0.45)
                if 0 < hp < 1:
                    put(img, rrect(SW - dp(24), dp(48), dp(12), SURFV), dp(12), ry - dp(2), 1 - hp)
            rx, rr = dp(28), dp(11)
            cy = ry + dp(22)
            d = ImageDraw.Draw(img)
            d.ellipse((rx, cy - rr, rx + 2 * rr, cy + rr), outline=Y if on else TXT3, width=dp(2))
            if on:
                pop = back_out(prog(t, 1.45, 0.3)) if k == 1 else 1
                r2 = dp(5.5) * min(pop, 1.2)
                d.ellipse((rx + rr - r2, cy - r2, rx + rr + r2, cy + r2), fill=Y)
            put(img, rich(opt, dp(16), 500, WHITE), dp(64), ry + dp(10))
    return img


def _vanish_chat(t):
    img = base()
    vt = prog(t, 4.45, 0.85)
    stack = Stack(SH - INPUT_H - dp(6))
    stack.add(sys_pill("Bugün"), None, 1.0, gap=dp(12))
    stack.add(sys_pill("⏱ Kaybolan mesajlar açıldı · 24 saat", Y, (40, 36, 16)), None, prog(t, 2.6, 0.3), gap=dp(14))
    b1 = bubble("Kapının şifresi 4821 🤫", True, "21:08", "read" if t > 3.3 else "sent", timer=True)
    b2 = bubble("Tamam, ezberledim 👌", False, "21:08", timer=True)
    if vt > 0:
        rad = vt * dp(6)
        b1 = b1.filter(ImageFilter.GaussianBlur(rad))
        b2 = b2.filter(ImageFilter.GaussianBlur(rad))
    op = 1 - ease_in_out(vt)
    stack.add(b1, True, prog(t, 2.95, 0.35), op=op, key="b1")
    stack.add(b2, False, prog(t, 3.45, 0.35), op=op, key="b2")
    stack.draw(img, STATUS_H + TOPBAR_H)
    # dağılan parçacıklar
    if 0 < vt < 1:
        import random
        rnd = random.Random(5)
        lay = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
        ld = ImageDraw.Draw(lay)
        for key in ("b1", "b2"):
            if key not in stack.rects:
                continue
            x, y, w, h = stack.rects[key]
            for _ in range(26):
                px, py = x + rnd.random() * w, y + rnd.random() * h
                ang = rnd.uniform(-math.pi * 0.9, -math.pi * 0.1)
                dist = dp(20) + rnd.random() * dp(70)
                qx = px + math.cos(ang) * dist * ease_out(vt)
                qy = py + math.sin(ang) * dist * ease_out(vt)
                r = dp(1.5 + rnd.random() * 2) * (1 - vt * 0.6)
                a = int(230 * math.sin(vt * math.pi))
                col = Y if rnd.random() < 0.6 else WHITE
                ld.ellipse((qx - r, qy - r, qx + r, qy + r), fill=col + (a,))
        img.alpha_composite(lay)
    gone = prog(t, 5.25, 0.35)
    if gone > 0:
        g = sys_pill("Mesajlar kayboldu", TXT3)
        put(img, g, (SW - g.width) / 2, SH - INPUT_H - dp(70), gone)
    input_bar(img)
    top_bar(img, "Ayşe", AVATAR[4], "A", subtitle="Kaybolan mesajlar · 24 saat")
    # "24 saat sonra" zaman atlama rozeti
    jp = min(prog(t, 3.95, 0.3), 1 - prog(t, 5.25, 0.3))
    if jp > 0:
        pw_, ph_ = dp(170), dp(44)
        chip = rrect(pw_, ph_, ph_ // 2, Y).copy()
        icon_timer(chip, dp(14), dp(10), dp(24), (20, 20, 20), angle=-math.pi / 2 + (t - 3.95) * 9)
        lt = rich("24 saat sonra…", dp(15), 700, (20, 20, 20))
        chip.alpha_composite(lt, (dp(46), (ph_ - lt.height) // 2))
        s = min(back_out(prog(t, 3.95, 0.35)), 1.1)
        c2 = chip.resize((max(2, int(pw_ * s)), max(2, int(ph_ * s))), Image.BILINEAR)
        put(img, c2, (SW - c2.width) / 2, STATUS_H + TOPBAR_H + dp(70) + (ph_ - c2.height) / 2, jp)
    status_bar(img)
    return img


def scene_vanish(t):
    t = max(t, 0.0)
    p = ease_in_out(prog(t, 2.2, 0.45))
    a = _info_screen(t) if p < 1 else None
    b = _vanish_chat(t) if p > 0 else None
    return (slide_between(a, b, p) if a is not None and b is not None else (a or b)).convert("RGB")


SCENE_FUNCS = {
    "1_sohbet": scene_chat,
    "2_qr": scene_qr,
    "3_anket": scene_poll,
    "4_tepki": scene_react,
    "5_yedek": scene_restore,
    "6_grup": scene_group,
    "7_foto": scene_photo,
    "8_asistan": scene_assistant,
    "9_kaybolan": scene_vanish,
}


if __name__ == "__main__":
    # Hızlı önizleme: her sahneden birkaç kare yan yana
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mock_sheet.png"
    names = sys.argv[2].split(",") if len(sys.argv) > 2 else list(SCENE_FUNCS)
    times = [float(x) for x in sys.argv[3].split(",")] if len(sys.argv) > 3 else [0.6, 1.5, 2.4, 3.6]
    sheet = Image.new("RGB", (len(times) * 320, len(names) * 700), (90, 90, 90))
    for r, fn in enumerate(SCENE_FUNCS[n] for n in names):
        for c, tt in enumerate(times):
            sheet.paste(fn(tt).resize((310, 689)), (c * 320, r * 700))
    sheet.save(out)
    print(out)
