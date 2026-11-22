"""Typewriter candidates from the owner's foley batch (WAV 48 kHz), by group, plus the current sounds for
comparison. Plain filtering (rumble and the generator's metallic top away), plain gain, soft edges; no limiter,
no pitch change. Usage: build4.py <batch_dir> <res_raw> <out_dir>"""
import os, sys, glob, subprocess, wave
import numpy as np
SR = 44100
batch, raw, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
F = 'highpass=f=70:p=2,lowpass=f=10000:p=2,lowpass=f=10000:p=2'
def load(path, filt=F):
    r = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-af', filt, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(r, dtype=np.float32).astype(np.float64)
def frames_db(x, fr=221):
    n = len(x) // fr
    return 20 * np.log10(np.array([np.sqrt(np.mean(x[i*fr:(i+1)*fr]**2)) for i in range(n)]) + 1e-12)
def span(x, thr=-30):
    db = frames_db(x); act = np.where(db > db.max() + thr)[0]
    return max(0, act[0] * 221 - 300), min(len(x), act[-1] * 221 + int(0.25 * SR))
def shape(seg, length=None, fade_in=0.002, tail=0.05):
    if length: seg = np.pad(seg[:int(length*SR)], (0, max(0, int(length*SR) - len(seg))))
    seg = seg.copy(); a = int(fade_in*SR); seg[:a] *= np.sin(np.linspace(0, np.pi/2, a))**2
    t = min(int(tail*SR), len(seg)//2); seg[-t:] *= np.cos(np.linspace(0, np.pi/2, t))**2
    return seg
def peak(x, db): return x * (10**(db/20) / (np.max(np.abs(x)) + 1e-12))
def save(name, x):
    with wave.open(os.path.join(out, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(x, -1, 1)*32767).astype(np.int16).tobytes())
groups = [('1', '1795345584', 0.22), ('2', '1795345616', 0.34), ('3', '1795345663', 0.34), ('4', '1795345695', None), ('5', '1795345722', None)]
for g, stamp, length in groups:
    for k, f in enumerate(sorted(glob.glob(os.path.join(batch, f'*-{stamp}*.wav')))):
        x = load(f); a, b = span(x)
        seg = x[a:b] if length is None else x[a:a + int(length*SR)]
        save(f'g{g}_{k+1}_{os.path.basename(f)[:8]}', peak(shape(seg, length, tail=0.06 if length else 0.15), -3))
for f in sorted(glob.glob(os.path.join(raw, 'tw_*.wav'))):
    save('A_' + os.path.basename(f)[:-4], load(f, 'anull'))
print('ok', len(os.listdir(out)))
