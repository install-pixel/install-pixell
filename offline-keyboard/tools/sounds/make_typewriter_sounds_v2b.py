"""Typewriter carriage returns (shortened), bells and new-sheet sounds from the owner's batches.
Usage: build5.py <batch_a> <batch_b> <out_dir>"""
import os, sys, glob, subprocess, wave
import numpy as np
SR = 44100
ba, bb, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
def load(path, filt):
    r = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-af', filt, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(r, dtype=np.float32).astype(np.float64)
def frames_db(x, fr=221):
    n = len(x) // fr
    return 20 * np.log10(np.array([np.sqrt(np.mean(x[i*fr:(i+1)*fr]**2)) for i in range(n)]) + 1e-12)
def fade(seg, fin, fout):
    seg = seg.copy(); a = int(fin*SR); seg[:a] *= np.sin(np.linspace(0, np.pi/2, a))**2
    t = int(fout*SR); seg[-t:] *= np.cos(np.linspace(0, np.pi/2, t))**2; return seg
def peak(x, db): return x * (10**(db/20) / (np.max(np.abs(x)) + 1e-12))
def save(name, x):
    with wave.open(os.path.join(out, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(x, -1, 1)*32767).astype(np.int16).tobytes())
F = 'highpass=f=70:p=2,lowpass=f=10000:p=2,lowpass=f=10000:p=2'
# Carriage: the last 1.1 s up to 0.35 s after the loudest stroke (the end of the slide and the bang).
n = 0
for stamp in ['1795345695', '1795345722']:
    for f in sorted(glob.glob(os.path.join(ba, f'*-{stamp}*.wav'))):
        x = load(f, F); db = frames_db(x); hit = int(np.argmax(db)) * 221
        a = max(0, hit - int(0.75 * SR)); b = min(len(x), hit + int(0.35 * SR))
        n += 1; save(f'return_{n:02d}_{os.path.basename(f)[:8]}', peak(fade(x[a:b], 0.15, 0.12), -3))
# Bells: they ring high; kept almost whole (B) or a little softer on top (C).
for k, f in enumerate(sorted(glob.glob(os.path.join(bb, '*-1795345758*.wav')))):
    for v, filt in (('B', 'highpass=f=300:p=2,lowpass=f=16000:p=2'), ('C', 'highpass=f=300:p=2,lowpass=f=12000:p=2,lowpass=f=12000:p=2')):
        x = load(f, filt); db = frames_db(x); act = np.where(db > db.max() - 40)[0]
        seg = x[max(0, act[0]*221 - 200):min(len(x), act[-1]*221 + int(0.3*SR))]
        save(f'bell_{k+1:02d}{v}_{os.path.basename(f)[:8]}', peak(fade(seg, 0.003, 0.25), -4))
# New sheet: the ratchet as the paper is rolled in, from its first click, up to 1.8 s.
for k, f in enumerate(sorted(glob.glob(os.path.join(bb, '*-1795345807*.wav')))):
    x = load(f, F); db = frames_db(x); s = max(0, int(np.argmax(db > db.max() - 14)) * 221 - 600)
    save(f'sheet_{k+1:02d}_{os.path.basename(f)[:8]}', peak(fade(x[s:s + int(1.8*SR)], 0.01, 0.3), -4))
print('ok', len(os.listdir(out)))
