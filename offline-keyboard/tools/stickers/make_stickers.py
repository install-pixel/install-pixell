"""Draws the keyboard's own animated stickers frame by frame (no outside images) and writes them as
animated WebP (with soft edges) and GIF (for apps that only take GIF) into the app's assets.

    python3 tools/stickers/make_stickers.py [output-dir [sticker ...]]

The hero is "Lingo", a round pastel blob, drawn as a boy (hair with a quiff) and as a girl (lashes, buns and
a bow): character stickers go to boy/ and girl/, the rest to common/. Every sticker is 256×256, drawn at 4× and scaled down for smooth
edges, with a white sticker outline so it reads on light and dark chats.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 256
SS = 3  # supersampling
C = SIZE * SS
FRAMES = 20
DURATION = 70  # ms per frame

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "app/src/main/assets/stickers"
OUT = ROOT
# Who Lingo is in the character stickers: "boy" or "girl" (the user picks one in the keyboard).
WHO = "boy"

INK = (52, 48, 64, 255)
WHITE = (255, 255, 255, 255)
PINK = (244, 160, 180, 255)
RED = (232, 86, 104, 255)
YELLOW = (255, 214, 102, 255)
ORANGE = (255, 158, 84, 255)
GREEN = (120, 200, 140, 255)
BLUE = (126, 176, 232, 255)
LAVENDER = (182, 164, 232, 255)
MINT = (140, 214, 190, 255)
PEACH = (246, 182, 150, 255)
SKY = (160, 205, 245, 255)


def s(v):
    return int(round(v * SS))


def canvas():
    img = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def ellipse(d, cx, cy, rx, ry, fill, outline=None, width=0):
    d.ellipse([s(cx - rx), s(cy - ry), s(cx + rx), s(cy + ry)], fill=fill, outline=outline, width=s(width) if width else 0)


def line(d, pts, fill=INK, width=6):
    d.line([(s(x), s(y)) for x, y in pts], fill=fill, width=s(width), joint="curve")
    for x, y in (pts[0], pts[-1]):
        r = width / 2
        ellipse(d, x, y, r, r, fill)


def arc(d, cx, cy, rx, ry, start, end, fill=INK, width=6):
    pts = []
    for i in range(25):
        a = math.radians(start + (end - start) * i / 24)
        pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    line(d, pts, fill, width)


def polygon(d, pts, fill, outline=None, width=0):
    d.polygon([(s(x), s(y)) for x, y in pts], fill=fill)
    if outline:
        line(d, list(pts) + [pts[0]], outline, width)


def heart_pts(cx, cy, size):
    pts = []
    for i in range(60):
        t = math.tau * i / 60
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * size / 32, cy + y * size / 32))
    return pts


def heart(d, cx, cy, size, color, outline=True):
    if outline:
        polygon(d, heart_pts(cx, cy, size + 10), WHITE)
    polygon(d, heart_pts(cx, cy, size), color)


def star_pts(cx, cy, r, rot=0.0, inner=0.45):
    pts = []
    for i in range(10):
        a = math.radians(rot - 90 + i * 36)
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def sparkle(d, cx, cy, r, color=YELLOW):
    polygon(d, star_pts(cx, cy, r, 0, 0.3), color)


BOY_HAIR = (92, 66, 52, 255)
GIRL_HAIR = (150, 86, 62, 255)
BOW = (240, 110, 150, 255)


def fringe(d, cx, cy, rx, ry, color):
    """Hair over the top of the head with a scalloped edge above the eyes."""
    top = [(cx + (rx + 2) * math.cos(math.radians(a)), cy + (ry + 2) * math.sin(math.radians(a))) for a in range(200, 341, 5)]
    edge_y = cy - ry * 0.5
    half = rx * math.sqrt(1 - 0.5 ** 2)
    bottom = []
    for k in range(25):
        x = cx + half - 2 * half * k / 24
        bottom.append((x, edge_y + 9 * abs(math.sin(k * math.pi / 6))))
    polygon(d, top + bottom, color)


def blob(d, cx, cy, r, color, squash=1.0, character=True):
    """Lingo: a round body with a white sticker edge and a soft darker rim, plus boy or girl hair."""
    rx, ry = r * (2 - squash), r * squash
    girl = character and WHO == "girl"
    if girl:
        # Buns behind the head.
        for sx in (-1, 1):
            bx, by, br = cx + sx * rx * 0.8, cy - ry * 0.72, rx * 0.3
            ellipse(d, bx, by, br + 8, br + 8, WHITE)
            ellipse(d, bx, by, br, br, GIRL_HAIR)
    ellipse(d, cx, cy, rx + 9, ry + 9, WHITE)
    shade = tuple(max(0, int(c * 0.86)) for c in color[:3]) + (255,)
    ellipse(d, cx, cy, rx, ry, shade)
    ellipse(d, cx - rx * 0.06, cy - ry * 0.06, rx * 0.93, ry * 0.93, color)
    ellipse(d, cx - rx * 0.42, cy - ry * 0.5, rx * 0.16, ry * 0.1, (255, 255, 255, 150))
    if character and WHO == "boy":
        quiff = [(cx - 16, cy - ry + 4), (cx - 2, cy - ry - 22), (cx + 10, cy - ry - 14), (cx + 18, cy - ry + 4)]
        polygon(d, quiff, BOY_HAIR, WHITE, 6)
        fringe(d, cx, cy, rx, ry, BOY_HAIR)
    elif girl:
        fringe(d, cx, cy, rx, ry, GIRL_HAIR)
        bx, by = cx + rx * 0.5, cy - ry * 0.86
        for sx in (-1, 1):
            polygon(d, [(bx, by), (bx + sx * 26, by - 15), (bx + sx * 26, by + 15)], BOW, WHITE, 5)
        ellipse(d, bx, by, 8, 8, BOW, WHITE, 2)
    return rx, ry


def eyes(d, cx, cy, gap=26, kind="open", size=1.0, character=True):
    for sx in (-1, 1):
        x = cx + sx * gap
        if character and WHO == "girl" and kind in ("open", "wide", "closed", "happy"):
            # Two lashes at the outer corner.
            lx, ly = x + sx * (9 if kind != "wide" else 12) * size, cy - (6 if kind in ("open", "wide") else 2) * size
            line(d, [(lx, ly), (lx + sx * 8, ly - 6)], INK, 3)
            line(d, [(lx - sx * 3, ly - 5), (lx + sx * 2, ly - 12)], INK, 3)
        if kind == "open":
            ellipse(d, x, cy, 9 * size, 12 * size, INK)
            ellipse(d, x + 3 * size, cy - 4 * size, 3.5 * size, 3.5 * size, WHITE)
        elif kind == "closed":
            arc(d, x, cy - 2, 10, 6, 20, 160, INK, 5)
        elif kind == "happy":
            arc(d, x, cy + 4, 10, 9, 200, 340, INK, 5)
        elif kind == "wide":
            ellipse(d, x, cy, 13 * size, 16 * size, WHITE, INK, 3)
            ellipse(d, x, cy + 2, 7 * size, 8 * size, INK)
        elif kind == "angry":
            ellipse(d, x, cy + 3, 8, 9, INK)
            line(d, [(x - sx * 14, cy - 14), (x + sx * 10, cy - 6)], INK, 5)


def cheeks(d, cx, cy, gap=44, alpha=150):
    for sx in (-1, 1):
        ellipse(d, cx + sx * gap, cy, 11, 7, (244, 140, 160, alpha))


def finish(frames, name):
    OUT.mkdir(parents=True, exist_ok=True)
    print(f"{OUT.name}/", end="")
    small = [f.resize((SIZE, SIZE), Image.LANCZOS) for f in frames]
    small[0].save(
        OUT / f"{name}.webp", save_all=True, append_images=small[1:], duration=DURATION, loop=0,
        quality=82, method=6,
    )
    small[0].save(
        OUT / f"{name}.gif", save_all=True, append_images=small[1:], duration=DURATION, loop=0, disposal=2,
        optimize=True,
    )
    print(f"{name}: {(OUT / f'{name}.webp').stat().st_size // 1024} KB webp, {(OUT / f'{name}.gif').stat().st_size // 1024} KB gif")


def phase(i):
    return math.tau * i / FRAMES


# ---------------------------------------------------------------------------- stickers


def hi():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        bob = 4 * math.sin(2 * t)
        blob(d, 120, 150 + bob, 78, SKY)
        eyes(d, 120, 138 + bob, kind="open")
        cheeks(d, 120, 162 + bob)
        arc(d, 120, 165 + bob, 16, 12, 20, 160, INK, 5)
        # waving hand
        ang = math.radians(-60 + 25 * math.sin(2 * t))
        hx, hy = 196 + 40 * math.cos(ang), 120 + 40 * math.sin(ang)
        line(d, [(185, 150 + bob), (hx, hy)], WHITE, 22)
        line(d, [(185, 150 + bob), (hx, hy)], SKY, 14)
        ellipse(d, hx, hy, 17, 17, WHITE)
        ellipse(d, hx, hy, 12, 12, SKY)
        out.append(img)
    finish(out, "01-hi")


def love():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        beat = 1 + 0.12 * max(0.0, math.sin(2 * t))
        blob(d, 128, 160, 78, PINK)
        eyes(d, 128, 145, kind="happy")
        cheeks(d, 128, 165, alpha=200)
        arc(d, 128, 168, 14, 10, 20, 160, INK, 5)
        heart(d, 128, 222, 34 * beat, RED)
        for k in range(3):
            p = ((i / FRAMES) + k / 3) % 1
            x = 60 + k * 70 + 10 * math.sin(phase(i) + k)
            y = 90 - 70 * p
            a = int(255 * (1 - p))
            heart(d, x, y, 14 + 6 * k, (232, 86, 104, a), outline=False)
        out.append(img)
    finish(out, "02-love")


def laugh():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        shake = 6 * math.sin(4 * t)
        blob(d, 128 + shake, 140, 82, YELLOW, squash=1 + 0.04 * math.sin(4 * t))
        eyes(d, 128 + shake, 120, kind="happy")
        ellipse(d, 128 + shake, 168, 30, 22 + 4 * abs(math.sin(4 * t)), INK)
        ellipse(d, 128 + shake, 180, 16, 8, RED)
        for sx in (-1, 1):
            ty = 125 + (i % 10) * 6
            ellipse(d, 128 + shake + sx * 64, ty, 6, 9, (120, 190, 240, 230))
        out.append(img)
    finish(out, "03-laugh")


def sad():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        blob(d, 128, 145, 80, BLUE)
        eyes(d, 128, 130, kind="open")
        arc(d, 128, 182, 18, 12, 200, 340, INK, 5)
        for k, sx in enumerate((-1, 1)):
            p = ((i / FRAMES) + k * 0.5) % 1
            ellipse(d, 128 + sx * 27, 145 + 80 * p, 6, 9, (90, 160, 230, int(255 * (1 - p))))
        out.append(img)
    finish(out, "04-sad")


def ok():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        blob(d, 110, 145, 76, MINT)
        eyes(d, 110, 130, kind="happy")
        cheeks(d, 110, 152)
        arc(d, 110, 158, 16, 12, 20, 160, INK, 5)
        p = min(1.0, i / (FRAMES * 0.6))
        pts = [(160, 200), (185, 225), (232, 170)]
        drawn = [pts[0]]
        if p < 0.4:
            q = p / 0.4
            drawn.append((pts[0][0] + (pts[1][0] - pts[0][0]) * q, pts[0][1] + (pts[1][1] - pts[0][1]) * q))
        else:
            q = (p - 0.4) / 0.6
            drawn += [pts[1], (pts[1][0] + (pts[2][0] - pts[1][0]) * q, pts[1][1] + (pts[2][1] - pts[1][1]) * q)]
        line(d, drawn, WHITE, 26)
        line(d, drawn, (60, 170, 100, 255), 16)
        out.append(img)
    finish(out, "05-ok")


def thanks():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        bow = 10 * max(0.0, math.sin(t))
        blob(d, 128, 150 + bow, 78, LAVENDER, squash=1 - 0.05 * max(0.0, math.sin(t)))
        eyes(d, 128, 140 + bow * 1.4, kind="closed")
        cheeks(d, 128, 160 + bow * 1.4)
        arc(d, 128, 166 + bow * 1.4, 12, 8, 20, 160, INK, 5)
        for k, (x, y) in enumerate([(50, 60), (205, 55), (215, 150)]):
            r = 14 + 8 * abs(math.sin(t + k))
            sparkle(d, x, y, r)
        out.append(img)
    finish(out, "06-thanks")


def sleep():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        breathe = 1 + 0.03 * math.sin(t)
        blob(d, 115, 160, 76, SKY, squash=breathe)
        eyes(d, 115, 150, kind="closed")
        ellipse(d, 115, 180, 8, 6, INK)
        for k in range(3):
            p = ((i / FRAMES) + k / 3) % 1
            x, y = 180 + 30 * p, 110 - 70 * p
            size = 14 + 14 * p
            a = int(255 * (1 - p))
            col = (90, 110, 170, a)
            line(d, [(x - size / 2, y - size / 2), (x + size / 2, y - size / 2), (x - size / 2, y + size / 2), (x + size / 2, y + size / 2)], col, 5)
        out.append(img)
    finish(out, "07-sleep")


def party():
    out = []
    colors = [RED, YELLOW, GREEN, BLUE, LAVENDER, ORANGE]
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        jump = -18 * abs(math.sin(t))
        blob(d, 128, 168 + jump, 72, PEACH)
        eyes(d, 128, 155 + jump, kind="happy")
        ellipse(d, 128, 185 + jump, 16, 12, INK)
        polygon(d, [(108, 100 + jump), (148, 100 + jump), (128, 40 + jump)], LAVENDER, WHITE, 6)
        ellipse(d, 128, 40 + jump, 8, 8, YELLOW)
        for k in range(14):
            x = (k * 47 + 13) % 240 + 8
            y = ((k * 31 + i * 11) % 250) - 10
            ang = (k * 40 + i * 30) % 180
            w, h = 7, 13
            c, sn = math.cos(math.radians(ang)), math.sin(math.radians(ang))
            pts = [(x + c * dx - sn * dy, y + sn * dx + c * dy) for dx, dy in ((-w, -h), (w, -h), (w, h), (-w, h))]
            polygon(d, [(px / 1, py / 1) for px, py in pts], colors[k % len(colors)])
        out.append(img)
    finish(out, "08-party")


def think():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        blob(d, 110, 160, 76, MINT)
        eyes(d, 120, 145, kind="open")
        line(d, [(98, 182), (128, 178)], INK, 5)
        n = 1 + (i * 3 // FRAMES)
        for k in range(n):
            ellipse(d, 165 + k * 28, 80 - k * 12, 9 + k * 2, 9 + k * 2, WHITE, INK, 3)
        out.append(img)
    finish(out, "09-think")


def angry():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        shake = 5 * math.sin(6 * t)
        blob(d, 128 + shake, 155, 78, (240, 130, 120, 255))
        eyes(d, 128 + shake, 145, kind="angry")
        arc(d, 128 + shake, 190, 16, 10, 200, 340, INK, 5)
        for sx in (-1, 1):
            p = (i / FRAMES * 2) % 1
            ellipse(d, 128 + sx * 70, 70 - 30 * p, 12 + 10 * p, 10 + 8 * p, (220, 220, 230, int(230 * (1 - p))))
        out.append(img)
    finish(out, "10-angry")


def coffee():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        polygon(d, [(64, 120), (176, 120), (166, 220), (74, 220)], WHITE)
        polygon(d, [(72, 128), (168, 128), (159, 212), (81, 212)], PEACH)
        arc(d, 182, 165, 26, 26, -80, 80, WHITE, 18)
        arc(d, 182, 165, 26, 26, -80, 80, PEACH, 10)
        ellipse(d, 120, 128, 48, 9, (120, 80, 60, 255))
        eyes(d, 120, 165, gap=18, kind="closed", character=False)
        arc(d, 120, 182, 10, 7, 20, 160, INK, 4)
        for k in range(3):
            x = 92 + k * 28
            pts = [(x + 8 * math.sin(t + y / 14 + k), y) for y in range(110, 40, -6)]
            line(d, pts, (200, 200, 210, 220), 6)
        out.append(img)
    finish(out, "11-coffee")


def big_heart():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        beat = 1 + 0.15 * max(0.0, math.sin(2 * t)) ** 2
        heart(d, 128, 135, 100 * beat, RED)
        ellipse(d, 92, 100, 14 * beat, 9 * beat, (255, 255, 255, 140))
        out.append(img)
    finish(out, "12-heart")


def flame_pts(cx, cy, r, wob):
    """A teardrop flame: round bottom, a tip that sways with [wob]."""
    tip = (cx + wob, cy - 2.3 * r)
    arc_pts = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(-25, 206, 10)]
    left, right = arc_pts[-1], arc_pts[0]

    def bez(p0, c, p1):
        return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
                 (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]) for t in (k / 12 for k in range(1, 12))]

    return arc_pts + bez(left, (cx - r * 0.9 + wob * 0.3, cy - 1.3 * r), tip) + [tip] + bez(tip, (cx + r * 0.9 + wob * 0.3, cy - 1.3 * r), right)


def fire():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        t = phase(i)
        wob = 14 * math.sin(2 * t)
        polygon(d, flame_pts(128, 172, 68, wob), WHITE)
        polygon(d, flame_pts(128, 172, 60, wob), RED)
        polygon(d, flame_pts(128, 184, 42, -wob * 0.8), ORANGE)
        polygon(d, flame_pts(128, 196, 24, wob * 0.6), YELLOW)
        for k in range(3):
            p = ((i / FRAMES) + k / 3) % 1
            ellipse(d, 80 + k * 48 + 8 * math.sin(t + k), 70 - 50 * p, 5, 5, (255, 158, 84, int(255 * (1 - p))))
        out.append(img)
    finish(out, "13-fire")


def star():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        rot = 360 * i / FRAMES / 5
        scale = 1 + 0.06 * math.sin(phase(i) * 2)
        polygon(d, star_pts(128, 132, 100 * scale + 10, rot, 0.47), WHITE)
        polygon(d, star_pts(128, 132, 100 * scale, rot, 0.45), YELLOW)
        eyes(d, 128, 128, gap=20, kind="happy", character=False)
        arc(d, 128, 145, 10, 7, 20, 160, INK, 4)
        for k in range(4):
            a = phase(i) + k * math.pi / 2
            sparkle(d, 128 + 112 * math.cos(a), 132 + 112 * math.sin(a), 10)
        out.append(img)
    finish(out, "14-star")


def yes():
    out = []
    pts = [(84, 132), (116, 164), (174, 100)]
    for i in range(FRAMES):
        img, d = canvas()
        r = 90 * (1 + 0.04 * math.sin(phase(i) * 2))
        ellipse(d, 128, 128, r + 10, r + 10, WHITE)
        ellipse(d, 128, 128, r, r, GREEN)
        p = min(1.0, 0.15 + i / (FRAMES * 0.5))
        if p < 0.4:
            q = p / 0.4
            drawn = [pts[0], (pts[0][0] + (pts[1][0] - pts[0][0]) * q, pts[0][1] + (pts[1][1] - pts[0][1]) * q)]
        else:
            q = (p - 0.4) / 0.6
            drawn = [pts[0], pts[1], (pts[1][0] + (pts[2][0] - pts[1][0]) * q, pts[1][1] + (pts[2][1] - pts[1][1]) * q)]
        line(d, drawn, WHITE, 18)
        out.append(img)
    finish(out, "15-yes")


def no():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        wob = 8 * math.sin(phase(i) * 2)
        ellipse(d, 128, 128, 100, 100, WHITE)
        ellipse(d, 128, 128, 90, 90, RED)
        for a in (45 + wob, -45 + wob):
            c, sn = math.cos(math.radians(a)), math.sin(math.radians(a))
            line(d, [(128 - 45 * c, 128 - 45 * sn), (128 + 45 * c, 128 + 45 * sn)], WHITE, 18)
        out.append(img)
    finish(out, "16-no")


def cat():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        blink = i in (8, 9)
        for sx in (-1, 1):
            polygon(d, [(128 + sx * 40, 92), (128 + sx * 82, 52), (128 + sx * 78, 118)], WHITE)
            polygon(d, [(128 + sx * 44, 98), (128 + sx * 76, 64), (128 + sx * 72, 114)], (200, 200, 205, 255))
        blob(d, 128, 150, 84, (215, 215, 222, 255), character=False)
        eyes(d, 128, 140, gap=30, kind="closed" if blink else "open", character=False)
        polygon(d, [(120, 160), (136, 160), (128, 168)], PINK)
        arc(d, 120, 168, 8, 6, 0, 150, INK, 4)
        arc(d, 136, 168, 8, 6, 30, 180, INK, 4)
        for sy in (-6, 6):
            line(d, [(70, 165 + sy), (40, 160 + sy * 2)], INK, 3)
            line(d, [(186, 165 + sy), (216, 160 + sy * 2)], INK, 3)
        tail = 20 * math.sin(phase(i))
        arc(d, 210, 215, 30, 20 + tail / 3, 180, 300, WHITE, 18)
        arc(d, 210, 215, 30, 20 + tail / 3, 180, 300, (200, 200, 205, 255), 10)
        out.append(img)
    finish(out, "17-cat")


def wow():
    out = []
    for i in range(FRAMES):
        img, d = canvas()
        p = (1 - math.cos(phase(i))) / 2
        blob(d, 128, 150, 78 + 6 * p, LAVENDER)
        eyes(d, 128, 135, kind="wide", size=1 + 0.35 * p)
        ellipse(d, 128, 185, 10 + 6 * p, 12 + 8 * p, INK)
        for k in range(3):
            a = math.radians(-120 + k * 30)
            line(d, [(128 + 105 * math.cos(a), 150 + 105 * math.sin(a)), (128 + (120 + 15 * p) * math.cos(a), 150 + (120 + 15 * p) * math.sin(a))], ORANGE, 7)
        out.append(img)
    finish(out, "18-wow")


CHARACTER = (hi, love, laugh, sad, ok, thanks, sleep, party, think, angry, wow)
COMMON = (coffee, big_heart, fire, star, yes, no, cat)

if __name__ == "__main__":
    only = set(sys.argv[2:])  # optional: names of the functions to redraw, e.g. "fire yes"
    for who in ("boy", "girl"):
        WHO = who
        OUT = ROOT / who
        for make in CHARACTER:
            if not only or make.__name__ in only:
                make()
    OUT = ROOT / "common"
    for make in COMMON:
        if not only or make.__name__ in only:
            make()
