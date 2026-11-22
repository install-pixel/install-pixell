"""Pencil set B2 from the owner's second batch (WAV 48 kHz): only plain filtering (rumble and the metallic top
away, a steep cut near 9 kHz), plain gain, soft edges; no limiter, no pitch change. Every candidate is saved
under a name that says where it came from, so the owner can pick by ear.
Usage: build2.py <batch_dir> <old_mp3_dir> <out_dir>"""
import os, sys, glob, subprocess, wave
import numpy as np

SR = 44100
batch, old, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
F = 'highpass=f=250:p=2,highpass=f=250:p=2,lowpass=f=9000:p=2,lowpass=f=9000:p=2,equalizer=f=7500:t=o:w=1:g=-2'
F_ERASER = 'highpass=f=120:p=2,lowpass=f=7000:p=2,lowpass=f=7000:p=2'


def load(path, filt=F):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-af', filt, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def file(prefix, folder=batch, ext='wav'):
    return glob.glob(os.path.join(folder, prefix + f'*.{ext}'))[0]


def frames_db(x, fr=441):
    n = len(x) // fr
    e = np.array([np.sqrt(np.mean(x[i * fr:(i + 1) * fr] ** 2)) for i in range(n)]) + 1e-12
    return 20 * np.log10(e)


def onset(x, thr=-18):
    db = frames_db(x)
    return max(0, int(np.argmax(db > db.max() + thr)) * 441 - 220)


def events(x, thr=-14, min_ms=60):
    """Separate strokes: runs of 10 ms frames within [thr] dB of the loudest, at least [min_ms] long."""
    db = frames_db(x)
    act = db > db.max() + thr
    out, i = [], 0
    while i < len(act):
        if act[i]:
            j = i
            while j < len(act) and act[j]:
                j += 1
            if (j - i) * 10 >= min_ms:
                out.append((max(0, i * 441 - 300), j * 441))
            i = j
        else:
            i += 1
    return out


def shape(seg, length, fade_in=0.005, tail=0.12):
    n = int(length * SR)
    seg = np.pad(seg[:n], (0, max(0, n - len(seg))))
    a = int(fade_in * SR)
    seg[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    t = int(tail * SR)
    seg[-t:] *= np.cos(np.linspace(0, np.pi / 2, t)) ** 2
    return seg


def level(seg, rms_db, peak_db=-3.0):
    act = seg[np.abs(seg) > np.max(np.abs(seg)) * 0.05]
    g = 10 ** (rms_db / 20) / (np.sqrt(np.mean(act ** 2)) + 1e-12)
    if np.max(np.abs(seg)) * g > 10 ** (peak_db / 20):
        g = 10 ** (peak_db / 20) / np.max(np.abs(seg))
    return seg * g


def save(name, seg):
    with wave.open(os.path.join(out, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(seg, -1, 1) * 32767).astype(np.int16).tobytes())


# Keys: the single strokes (sin, short batch), the slightly longer ones (reco), the old ones the owner liked,
# and strokes cut out of the written lines (one).
keys = []
for p in ['3667f11e', '57580448', '9a51c4cf', 'a0317f9e', '05f10b2c', '19ef2cd8', '1e1b5586']:
    x = load(file(p))
    keys.append((p, x[onset(x):]))
x = load(file('1d59e747', old, 'mp3'))
keys.append(('1d59e747', x[onset(x):]))
for p in ['b63648f4', 'c3815408']:
    x = load(file(p))
    for k, (a, b) in enumerate(events(x)[:3]):
        keys.append((f'{p}-{k + 1}', x[a:b + int(0.1 * SR)]))
for i, (src, g) in enumerate(keys):
    save(f'key_{i + 1:02d}_{src}', level(shape(g, 0.26, tail=0.14), -22 + (i % 3 - 1) * 0.8))

# Dots: one tap each, cut out of the «many dots» files (a_s).
dots = []
for p in ['08d3b4fc', '0a0ab0a7', '0efb734c', '8fd5d570']:
    x = load(file(p))
    for k, (a, b) in enumerate(events(x, thr=-12, min_ms=10)[:2]):
        dots.append((f'{p}-{k + 1}', x[a:a + int(0.07 * SR)]))
for i, (src, g) in enumerate(dots):
    save(f'space_{i + 1:02d}_{src}', level(shape(g, 0.07, fade_in=0.002, tail=0.04), -24))
x = load(file('8ec98a95', old, 'mp3'))
save('space_00_8ec98a95-old', level(shape(x[onset(x):], 0.07, fade_in=0.003, tail=0.04), -26))

# Return: a longer stroke and, a moment later, one dot.
strokes = [('d702d278-old', load(file('d702d278', old, 'mp3'))), ('19ef2cd8', load(file('19ef2cd8'))), ('05f10b2c', load(file('05f10b2c')))]
for i, (src, x) in enumerate(strokes):
    st = shape(x[onset(x):], 0.36, tail=0.16)
    d = shape(dots[i % len(dots)][1], 0.07, fade_in=0.002, tail=0.04)
    ret = np.zeros(int(0.52 * SR))
    ret[:len(st)] += level(st, -22)
    s = int(0.42 * SR)
    ret[s:s + len(d)] += level(d, -24)[:len(ret) - s]
    save(f'return_{i + 1:02d}_{src}+dot', ret)

# Eraser: single rubs cut out of the short-rub files, and the continuous rubbing looped for a held Backspace.
ER = sys.argv[4] if len(sys.argv) > 4 else batch
n = 0
for p in ['47fcc15f', '54cbc453', '5f2f9d97', 'fc566e77']:
    x = load(file(p, ER), F_ERASER)
    ev = sorted(events(x, thr=-12, min_ms=20), key=lambda e: -np.max(np.abs(x[e[0]:e[1]])))[:2]
    for a, b in ev:
        n += 1
        save(f'erase_{n:02d}_{p}', level(shape(x[a:], 0.28, fade_in=0.008, tail=0.12), -24))
for i, p in enumerate(['b8012b92', '54d4b551', 'ba55a3de', 'da094494']):
    x = load(file(p, ER), F_ERASER)
    x = x[onset(x):]
    hold = x[int(0.3 * SR):int(2.5 * SR)].copy()
    c = int(0.2 * SR)
    hold[:c] = hold[:c] * np.linspace(0, 1, c) + hold[-c:] * np.linspace(1, 0, c)
    save(f'erasehold_{i + 1:02d}_{p}', level(hold[:-c], -26))

# The long pencil lines (sin, 3 s): a long stroke and a dot for Return, and strokes for keys.
for i, p in enumerate(['31d25c4b', '7b127d05', '628b62f2']):
    x = load(file(p))
    x = x[onset(x):]
    st = shape(x, 0.36, tail=0.16)
    d = shape(dots[(i + 3) % len(dots)][1], 0.07, fade_in=0.002, tail=0.04)
    ret = np.zeros(int(0.52 * SR))
    ret[:len(st)] += level(st, -22)
    s0 = int(0.42 * SR)
    ret[s0:s0 + len(d)] += level(d, -24)[:len(ret) - s0]
    save(f'return_{i + 4:02d}_{p}+dot', ret)
x = load(file('66e6abbf'))
for k, (a, b) in enumerate(events(x)[:3]):
    save(f'key_{14 + k:02d}_66e6abbf-{k + 1}', level(shape(x[a:b + int(0.1 * SR)], 0.26, tail=0.14), -22))
F_ERASER_NOTE = 1
print('ok', len(os.listdir(out)))
