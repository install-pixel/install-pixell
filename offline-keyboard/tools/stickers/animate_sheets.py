"""Animates the Lingo sticker sheets (tools/stickers/source/boy.png and girl.png, 3×3 stickers each) and
writes them to the app's assets as animated GIF (sent to chat apps, which play GIF) and WebP (shown in the
keyboard's sticker panel, with soft edges).

    pip install numpy scipy pillow
    python3 tools/stickers/animate_sheets.py [output-dir]
    python3 tools/stickers/animate_sheets.py --telegram [output-dir]   # also needs ffmpeg with libvpx-vp9

Telegram turns a GIF from a keyboard into a video, which has no transparency (a white square). With
--telegram the same stickers are written as Telegram video stickers instead (512 px WebM VP9 with alpha,
under 3 seconds and 256 KB, no sound), to upload once with the @Stickers bot as the user's own pack.

The boy sheet has real transparency. The girl sheet has a grey-and-white checkerboard painted into the
picture; it is removed here (flood fill from the edges, plus enclosed checkerboard areas).
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi

HERE = Path(__file__).resolve().parent
TELEGRAM = "--telegram" in sys.argv
ARGS = [a for a in sys.argv[1:] if a != "--telegram"]
OUT = Path(ARGS[0]) if ARGS else HERE.parents[1] / ("stickers-telegram" if TELEGRAM else "app/src/main/assets/stickers")

SIZE = 512 if TELEGRAM else 256  # output square
FRAMES = 14
DURATION = 70  # ms per frame
GIF_MATTE = (245, 245, 245)  # colour the soft edges are blended with in GIF (GIF has no partial transparency)

# name, motion, overlays; positions are fractions of the sticker square.
BOY = [
    ("01-idea", "bob", [("glow", 0.80, 0.70)]),
    ("02-rainbow", "pulse", [("hearts", 0.80, 0.30)]),
    ("03-rocket", "rocket", []),
    ("04-alien", "tilt", []),
    ("05-wow", "pop", []),
    ("06-thumbs-up", "bounce", [("sparkles", 0.85, 0.40)]),
    ("07-laptop", "type", []),
    ("08-space", "float", [("sparkles", 0.75, 0.20)]),
    ("09-coffee", "sway", []),
]
GIRL = [
    ("01-glasses", "bob", []),
    ("02-thumbs-up", "bounce", [("sparkles", 0.85, 0.20)]),
    ("03-rocket", "rocket", []),
    ("04-dreamy", "sway", [("hearts", 0.80, 0.20)]),
    ("05-heart", "pulse", []),
    ("06-peace", "tilt", [("sparkles", 0.88, 0.25)]),
    ("07-relax", "breathe", []),
    ("08-wow", "pop", []),
    ("09-star", "bounce", [("sparkles", 0.20, 0.35)]),
]


# ------------------------------------------------------------------ sheet → stickers

def remove_checkerboard(im):
    a = np.asarray(im.convert("RGB")).astype(int)
    mx, mn = a.max(2), a.min(2)
    cand = (mx - mn <= 12) & (mn >= 222)
    lab, n = ndi.label(cand)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    sizes = ndi.sum(np.ones_like(lab), lab, range(1, n + 1))
    for i, s in enumerate(sizes, 1):
        if i in border or s < 400:
            continue
        vals = mn[lab == i]
        # Both checker tones present: an enclosed piece of background, not a white part of the drawing.
        if ((vals >= 228) & (vals <= 242)).mean() > 0.2 and (vals >= 248).mean() > 0.2:
            bg |= lab == i
    fg = ndi.binary_opening(~bg, iterations=1)
    alpha = Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
    out = im.convert("RGBA")
    out.putalpha(alpha)
    return out


def only_this_sticker(cell):
    """Drops pieces of the neighbouring stickers that reach into this cell: the biggest shape stays, and so
    do small ones (stars, hearts) that do not touch the cell's edge."""
    solid = np.asarray(cell.getchannel("A")) > 16
    lab, n = ndi.label(solid)
    if n <= 1:
        return cell
    sizes = ndi.sum(solid, lab, range(1, n + 1))
    main = int(np.argmax(sizes)) + 1
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    keep = [i for i in range(1, n + 1) if i == main or i not in edge]
    mask = np.isin(lab, keep)
    alpha = np.asarray(cell.getchannel("A")) * mask
    out = cell.copy()
    out.putalpha(Image.fromarray(alpha.astype(np.uint8)))
    return out


def stickers(sheet):
    w, h = sheet.size
    cells = []
    for r in range(3):
        for c in range(3):
            cell = sheet.crop((c * w // 3, r * h // 3, (c + 1) * w // 3, (r + 1) * h // 3))
            cell = only_this_sticker(cell)
            box = cell.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
            cell = cell.crop(box)
            # Into a square with room for the motion.
            side = int(max(cell.size) * 1.18)
            square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
            square.alpha_composite(cell, ((side - cell.width) // 2, (side - cell.height) // 2))
            cells.append(square.resize((SIZE, SIZE), Image.LANCZOS))
    return cells


# ------------------------------------------------------------------ motion

def transform(img, dx=0.0, dy=0.0, scale_x=1.0, scale_y=1.0, angle=0.0, anchor=(0.5, 0.92)):
    """Moves, scales and rotates [img] around [anchor] (fractions), keeping the canvas size."""
    w, h = img.size
    ax, ay = anchor[0] * w, anchor[1] * h
    big = img.resize((max(1, int(w * scale_x)), max(1, int(h * scale_y))), Image.BICUBIC)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    # An empty canvas: a plain paste copies the pixels (and clips anything that moves off the edge).
    canvas.paste(big, (int(round(ax - ax * scale_x + dx)), int(round(ay - ay * scale_y + dy))))
    if angle:
        canvas = canvas.rotate(angle, resample=Image.BICUBIC, center=(ax, ay))
    return canvas


def motion(kind, img, t):
    s = math.sin(2 * math.pi * t)
    if kind == "bob":
        return transform(img, dy=-6 * s)
    if kind == "bounce":
        up = abs(math.sin(math.pi * t))
        squash = 1 - 0.06 * max(0.0, math.cos(2 * math.pi * t))
        return transform(img, dy=-18 * up, scale_x=2 - squash, scale_y=squash)
    if kind == "pulse":
        k = 1 + 0.06 * max(0.0, math.sin(4 * math.pi * t)) ** 2
        return transform(img, scale_x=k, scale_y=k, anchor=(0.5, 0.6))
    if kind == "pop":
        k = 1 + 0.08 * max(0.0, math.sin(2 * math.pi * t)) ** 3
        return transform(img, scale_x=k, scale_y=k, anchor=(0.5, 0.55))
    if kind == "tilt":
        return transform(img, angle=7 * s)
    if kind == "sway":
        return transform(img, angle=4 * s, dx=3 * s)
    if kind == "breathe":
        k = 1 + 0.025 * s
        return transform(img, scale_x=2 - k, scale_y=k)
    if kind == "type":
        return transform(img, dy=-2.5 * abs(math.sin(6 * math.pi * t)))
    if kind == "float":
        return transform(img, dy=-9 * s, angle=2 * math.cos(2 * math.pi * t))
    if kind == "rocket":
        jitter_x = 2.5 * math.sin(14 * math.pi * t)
        jitter_y = 2.0 * math.cos(18 * math.pi * t)
        rise = -10 * math.sin(2 * math.pi * t)
        return transform(img, dx=jitter_x + 6 * math.sin(2 * math.pi * t), dy=jitter_y + rise, angle=-2 * math.sin(2 * math.pi * t))
    return img


# ------------------------------------------------------------------ overlays (drawn at 3× and scaled down)

def star_points(cx, cy, r, inner=0.38):
    pts = []
    for i in range(8):
        a = math.radians(-90 + i * 45)
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def heart_points(cx, cy, size):
    pts = []
    for i in range(48):
        t = 2 * math.pi * i / 48
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * size / 32, cy + y * size / 32))
    return pts


def overlay(kind, fx, fy, t):
    k = 3
    layer = Image.new("RGBA", (SIZE * k, SIZE * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = fx * SIZE * k, fy * SIZE * k
    if kind == "sparkles":
        for i, (ox, oy) in enumerate([(0, 0), (-0.09, 0.12), (0.06, 0.2)]):
            phase = (t + i / 3) % 1
            r = (6 + 9 * abs(math.sin(math.pi * phase))) * k
            alpha = int(255 * abs(math.sin(math.pi * phase)))
            d.polygon(star_points(cx + ox * SIZE * k, cy + oy * SIZE * k, r), fill=(255, 214, 64, alpha))
    elif kind == "hearts":
        for i in range(3):
            phase = (t + i / 3) % 1
            x = cx + (i - 1) * 0.08 * SIZE * k + 6 * k * math.sin(2 * math.pi * (phase + i))
            y = cy + 0.12 * SIZE * k - phase * 0.3 * SIZE * k
            alpha = int(255 * (1 - phase))
            d.polygon(heart_points(x, y, (14 + 4 * i) * k), fill=(240, 80, 110, alpha))
    elif kind == "steam":
        for i in range(3):
            phase = (t + i / 3) % 1
            x0 = cx + (i - 1) * 0.05 * SIZE * k
            pts = []
            for j in range(10):
                y = cy - (j * 0.025 + phase * 0.12) * SIZE * k
                pts.append((x0 + 5 * k * math.sin(j * 0.9 + 2 * math.pi * phase), y))
            d.line(pts, fill=(255, 255, 255, int(200 * (1 - phase))), width=4 * k, joint="curve")
    elif kind == "glow":
        r = (22 + 8 * math.sin(2 * math.pi * t)) * k
        for step in range(6, 0, -1):
            rr = r * step / 6
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 230, 120, int(26 * (7 - step) / 6)))
    return layer.resize((SIZE, SIZE), Image.LANCZOS)


# ------------------------------------------------------------------ output

def gif_frame(frame):
    """Hard edges for GIF: pixels over half opaque stay, blended with a light matte; the rest is clear."""
    rgb = Image.new("RGB", frame.size, GIF_MATTE)
    rgb.paste(frame, mask=frame.getchannel("A"))
    p = rgb.quantize(colors=160, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
    mask = frame.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    p.paste(255, mask=Image.eval(mask, lambda v: 255 - v))
    p.info["transparency"] = 255
    return p


def write(folder, name, frames):
    folder.mkdir(parents=True, exist_ok=True)
    frames[0].save(folder / f"{name}.webp", save_all=True, append_images=frames[1:], duration=DURATION, loop=0,
                   quality=72, method=6)
    gifs = [gif_frame(f) for f in frames]
    gifs[0].save(folder / f"{name}.gif", save_all=True, append_images=gifs[1:], duration=DURATION, loop=0,
                 disposal=2, transparency=255, optimize=False)
    print(f"{folder.name}/{name}: {(folder / f'{name}.webp').stat().st_size // 1024} KB webp, "
          f"{(folder / f'{name}.gif').stat().st_size // 1024} KB gif")


def write_telegram(folder, name, frames):
    """Telegram video sticker: the loop played three times (just under 3 s), VP9 with alpha."""
    import subprocess
    import tempfile
    folder.mkdir(parents=True, exist_ok=True)
    out = folder / f"{folder.name}-{name}.webm"
    with tempfile.TemporaryDirectory() as tmp:
        for i, f in enumerate(frames * 3):
            f.save(Path(tmp) / f"{i:03d}.png")
        for bitrate in ("400k", "300k", "200k", "150k"):
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", f"1000/{DURATION}", "-i", f"{tmp}/%03d.png",
                            "-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-b:v", bitrate, "-an", "-auto-alt-ref", "0",
                            str(out)], check=True)
            if out.stat().st_size <= 250 * 1024:
                break
    print(f"{out.name}: {out.stat().st_size // 1024} KB")


def build(sheet_file, specs, folder):
    sheet = Image.open(sheet_file)
    sheet = sheet.convert("RGBA") if sheet.mode == "RGBA" else remove_checkerboard(sheet)
    for img, (name, kind, overlays) in zip(stickers(sheet), specs):
        frames = []
        for i in range(FRAMES):
            t = i / FRAMES
            frame = motion(kind, img, t)
            for o in overlays:
                frame.alpha_composite(overlay(o[0], o[1], o[2], t))
            frames.append(frame)
        (write_telegram if TELEGRAM else write)(folder, name, frames)


if __name__ == "__main__":
    build(HERE / "source/boy.png", BOY, OUT / "boy")
    build(HERE / "source/girl.png", GIRL, OUT / "girl")
