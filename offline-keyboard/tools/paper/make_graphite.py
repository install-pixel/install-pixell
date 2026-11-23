"""The dark sketch's paper: the diary's graphite page (its PaperTexture.sheet and naturalRelief, ported), written as
the two pictures the keyboard loads in place of the crumpled photograph (calmer: the writing reads on it).
Run: python make_graphite.py <res/drawable-nodpi folder>"""
import math, random
import numpy as np
from PIL import Image

W, H = 540, 1080
RW, RH = 270, 540

class ValueNoise:
    def __init__(self, rnd, cell):
        self.cell = cell
        self.size = 64
        self.grid = np.array([rnd.random() for _ in range(64 * 64)], dtype=np.float32).reshape(64, 64)
    def grid_at(self, xs, ys):
        gx = xs / self.cell
        gy = ys / self.cell
        x0 = np.floor(gx).astype(int)
        y0 = np.floor(gy).astype(int)
        tx = gx - x0; ty = gy - y0
        tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty)
        g = lambda i, j: self.grid[np.mod(j, 64), np.mod(i, 64)]
        a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * tx
        b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * tx
        return a + (b - a) * ty

def sheet(seed=11, base=(21, 19, 15), mottle=7.0, grain=3.5, fibers=2200):
    rnd = random.Random(seed)
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
    n1, n2, n3 = ValueNoise(rnd, 170), ValueNoise(rnd, 60), ValueNoise(rnd, 18)
    v = (n1.grid_at(xs, ys) * 0.6 + n2.grid_at(xs, ys) * 0.3 + n3.grid_at(xs, ys) * 0.1 - 0.5) * 2 * mottle
    v += (np.random.default_rng(seed).random((H, W)) - 0.5) * 2 * grain
    for _ in range(fibers):
        fx, fy, a = rnd.random() * W, rnd.random() * H, rnd.random() * 6.28
        ln = 6 + rnd.randrange(18); b = 4 + rnd.random() * 6
        for s in range(ln):
            ix, iy = int(fx), int(fy)
            if 0 <= ix < W and 0 <= iy < H:
                v[iy, ix] += b * (1 - s / ln)
            a += (rnd.random() - 0.5) * 0.5
            fx += math.cos(a); fy += math.sin(a)
    for _ in range(W * H // 1500):
        v[rnd.randrange(H), rnd.randrange(W)] += 10 + rnd.random() * 10
    r = np.clip(base[0] + v, 0, 255); g = np.clip(base[1] + v * 0.94, 0, 255); bl = np.clip(base[2] + v * 0.82, 0, 255)
    return np.stack([r, g, bl], -1)

def natural_relief(seed):
    rnd = random.Random(seed)
    r = np.zeros((RH, RW), np.float32)
    def walk(x, y, a, ln, bend):
        pts = []; c = (rnd.random() - 0.5) * bend
        for _ in range(max(4, int(ln / 1.5))):
            pts.append((x, y))
            c = max(-bend, min(bend, c + (rnd.random() - 0.5) * bend * 0.3))
            a += c + (rnd.random() - 0.5) * bend * 2
            x += math.cos(a) * 1.5; y += math.sin(a) * 1.5
        return pts
    def stamp(pts, amp, width):
        if len(pts) < 2: return
        reach = width * 3
        P = np.array(pts, np.float32)
        x0 = int(max(0, P[:, 0].min() - reach)); x1 = int(min(RW - 1, P[:, 0].max() + reach))
        y0 = int(max(0, P[:, 1].min() - reach)); y1 = int(min(RH - 1, P[:, 1].max() + reach))
        if x1 <= x0 or y1 <= y0: return
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1].astype(np.float32)
        dist = np.full(xs.shape, np.inf, np.float32); taper = np.zeros(xs.shape, np.float32)
        n = len(pts)
        for i in range(n - 1):
            p, q = P[i], P[i + 1]
            dx, dy = q - p; l = max(math.hypot(dx, dy), 0.001); ux, uy = dx / l, dy / l
            tp = math.sqrt(math.sin(math.pi * i / (n - 1)))
            vx = xs - p[0]; vy = ys - p[1]
            t = vx * ux + vy * uy
            d = -vx * uy + vy * ux
            m = (t >= -0.2) & (t <= l + 0.2) & (np.abs(d) < np.abs(dist))
            dist[m] = d[m]; taper[m] = tp
        m = np.abs(dist) <= reach
        s = dist[m] / width
        sub = r[y0:y1 + 1, x0:x1 + 1]
        sub[m] += amp * taper[m] * s * np.exp(-s * s) * 2.3
    def crease(x, y, a, ln, amp, width, branches, bend):
        pts = walk(x, y, a, ln, bend); stamp(pts, amp, width)
        for _ in range(branches):
            i = min(max(int(len(pts) * (0.2 + rnd.random() * 0.6)), 0), len(pts) - 2)
            at, nx = pts[i], pts[i + 1]
            b = math.atan2(nx[1] - at[1], nx[0] - at[0])
            turn = (0.4 + rnd.random() * 0.7) * (1 if rnd.random() < 0.5 else -1)
            stamp(walk(at[0], at[1], b + turn, ln * (0.15 + rnd.random() * 0.25), bend * 2), amp * 0.6, width * 0.8)
    for _ in range(2 + rnd.randrange(3)):
        left = rnd.random() < 0.5
        y0 = rnd.random() * RH; x0 = -10 if left else RW + 10
        a = (0 if left else math.pi) + (rnd.random() - 0.5) * 1.6
        crease(x0, y0, a, (RW + RH) * (0.5 + rnd.random() * 0.5), 6.5, 3.2, 1 + rnd.randrange(3), 0.0025)
    for _ in range(45 + rnd.randrange(25)):
        crease(rnd.random() * RW, rnd.random() * RH, rnd.random() * 6.28, 8 + rnd.random() * 40, 2.2 + rnd.random() * 1.5, 1.4, 0, 0.02)
    ys, xs = np.mgrid[0:RH, 0:RW]
    e = np.minimum(np.minimum(xs, RW - 1 - xs), np.minimum(ys, RH - 1 - ys)).astype(np.float32)
    band = RW * 0.05
    k = np.clip(1 - e / band, 0, 1)
    r += 13 * k * k * 0.85
    return r


def make(w, h, seed, rseed):
    global W, H, RW, RH
    W, H, RW, RH = w, h, max(2, w // 2), max(2, h // 2)
    paint = sheet(seed=seed)
    rel = natural_relief(rseed)
    rel = np.array(Image.fromarray(rel).resize((w, h), Image.BILINEAR))
    shade = np.clip(1 + np.minimum(0, rel) / 20, 0, 1)[..., None]
    light = np.clip(np.maximum(0, rel) / 255 * 1.15, 0, 1)[..., None]
    img = 255 - (255 - paint * shade) * (1 - light)
    return Image.fromarray(img.astype(np.uint8))


if __name__ == "__main__":
    import sys
    out = sys.argv[1].rstrip("/\\") + "/"
    # Made at the diary's scale (its grain two screen pixels wide) and doubled.
    make(724, 543, 11, 101).resize((1448, 1086), Image.BILINEAR).save(out + "sheet_keys_dark.jpg", quality=92)
    make(400, 400, 17, 139).resize((800, 800), Image.BILINEAR).save(out + "sheet_cards_dark.jpg", quality=92)
