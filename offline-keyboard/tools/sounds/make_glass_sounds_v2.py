"""Bubble-wrap candidates from the owner's foley batch (WAV 48 kHz): plain filtering, plain gain, soft edges;
no limiter, no pitch change. Named by source so the owner picks by ear. Usage: build3.py <batch_dir> <out_dir>"""
import os, sys, glob, subprocess, wave
import numpy as np

SR = 44100
batch, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
F = 'highpass=f=120:p=2,highpass=f=120:p=2,lowpass=f=10000:p=2,lowpass=f=10000:p=2'
F_AIR = 'highpass=f=200:p=2,lowpass=f=6000:p=2,lowpass=f=6000:p=2'
F_RUSTLE = 'highpass=f=150:p=2,lowpass=f=8000:p=2,lowpass=f=8000:p=2'


def load(prefix, filt=F):
    path = glob.glob(os.path.join(batch, prefix + '*.wav'))[0]
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-af', filt, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def frames_db(x, fr=221):
    n = len(x) // fr
    e = np.array([np.sqrt(np.mean(x[i * fr:(i + 1) * fr] ** 2)) for i in range(n)]) + 1e-12
    return 20 * np.log10(e)


def pops_in(x, thr=-14, gap_ms=60):
    """Each pop's start: a frame within [thr] dB of the loudest after at least [gap_ms] of quiet."""
    db = frames_db(x)
    top = db.max()
    starts, last = [], -10 ** 9
    for i, v in enumerate(db):
        if v > top + thr and (i - last) * 5 > gap_ms:
            starts.append(max(0, i * 221 - 120))
        if v > top + thr:
            last = i
    return starts


def shape(seg, length, fade_in=0.002, tail=0.06):
    n = int(length * SR)
    seg = np.pad(seg[:n], (0, max(0, n - len(seg))))
    a = int(fade_in * SR)
    seg[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    t = int(tail * SR)
    seg[-t:] *= np.cos(np.linspace(0, np.pi / 2, t)) ** 2
    return seg


def peak(seg, db):
    return seg * (10 ** (db / 20) / (np.max(np.abs(seg)) + 1e-12))


def rms(seg, db, ceiling=-3.0):
    act = seg[np.abs(seg) > np.max(np.abs(seg)) * 0.05]
    g = 10 ** (db / 20) / (np.sqrt(np.mean(act ** 2)) + 1e-12)
    if np.max(np.abs(seg)) * g > 10 ** (ceiling / 20):
        g = 10 ** (ceiling / 20) / np.max(np.abs(seg))
    return seg * g


def save(name, seg):
    with wave.open(os.path.join(out, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(seg, -1, 1) * 32767).astype(np.int16).tobytes())


# Keys: the small pops, and each pop of the «three in a row» files on its own.
keys = []
for p in ['66199933', '3d5158ad', '6e153d75', 'c74e7da8']:
    x = load(p)
    keys.append((p, x[pops_in(x)[0]:]))
exa = {}
for p in ['c39435e9', '3704895b', '9e235e0a', 'cf41d63e']:
    x = load(p)
    exa[p] = [x[s:] for s in pops_in(x, thr=-10, gap_ms=300)]
    for k, s in enumerate(exa[p]):
        keys.append((f'{p}-{k + 1}', s))
for i, (src, g) in enumerate(keys):
    save(f'key_{i + 1:02d}_{src}', peak(shape(g, 0.14), -5 + (i % 3 - 1) * 0.8))

# Space: the bigger, juicier pops.
for i, p in enumerate(['47c83feb', '82cad485', '8b6e988c', 'ac0a7f61']):
    x = load(p)
    save(f'space_{i + 1:02d}_{p}', peak(shape(x[pops_in(x)[0]:], 0.25, tail=0.15), -4))

# Return: three pops quickly in a row (as a finger along a line of bubbles), from each «three» file's pops.
for i, p in enumerate(exa):
    ps = exa[p][:3]
    if len(ps) < 3:
        continue
    ret = np.zeros(int(0.42 * SR))
    for j, at in enumerate([0.0, 0.09, 0.19]):
        g = peak(shape(ps[j], 0.14), -5 - j * 0.5)
        s0 = int(at * SR)
        ret[s0:s0 + len(g)] += g[:len(ret) - s0]
    save(f'return_{i + 1:02d}_{p}', ret)

# Backspace: a short soft puff of air (the long hiss shortened and softened).
for i, p in enumerate(['42e2c9a9', 'd96de6af', 'c3072f06', '5139899e']):
    x = load(p, F_AIR)
    db = frames_db(x)
    s0 = max(0, int(np.argmax(db > db.max() - 12)) * 221 - 200)
    save(f'delete_{i + 1:02d}_{p}', rms(shape(x[s0:], 0.2, fade_in=0.015, tail=0.12), -27))

# Held Backspace: the wrap rustling, looped (its end runs into its start).
for i, p in enumerate(['61faf9c8', '81368e11', '7563a9c6', '8b72bf8d']):
    x = load(p, F_RUSTLE)
    hold = x[int(0.25 * SR):int(2.35 * SR)].copy()
    c = int(0.2 * SR)
    hold[:c] = hold[:c] * np.linspace(0, 1, c) + hold[-c:] * np.linspace(1, 0, c)
    save(f'deletehold_{i + 1:02d}_{p}', rms(hold[:-c], -28))
print('ok', len(os.listdir(out)))
