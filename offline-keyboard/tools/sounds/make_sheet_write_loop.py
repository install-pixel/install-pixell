"""The pencil writing on: a seamless loop of a continuous pencil line (owner's foley), for the sketch's typing.
Usage: loop.py <batch_dir> <out_wav_dir>"""
import os, sys, glob, subprocess, wave
import numpy as np
SR = 44100
batch, out = sys.argv[1], sys.argv[2]
F = 'highpass=f=250:p=2,highpass=f=250:p=2,lowpass=f=9000:p=2,lowpass=f=9000:p=2,equalizer=f=7500:t=o:w=1:g=-2'
def load(prefix):
    path = glob.glob(os.path.join(batch, prefix + '*.wav'))[0]
    r = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-af', F, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(r, dtype=np.float32).astype(np.float64)
for k, p in enumerate(['628b62f2', '31d25c4b', '7b127d05']):
    x = load(p)
    seg = x[int(0.35 * SR):int(2.55 * SR)].copy()
    c = int(0.25 * SR)
    seg[:c] = seg[:c] * np.sin(np.linspace(0, np.pi / 2, c)) ** 2 + seg[-c:] * np.cos(np.linspace(0, np.pi / 2, c)) ** 2
    seg = seg[:-c]
    act = seg[np.abs(seg) > np.max(np.abs(seg)) * 0.05]
    g = 10 ** (-24 / 20) / (np.sqrt(np.mean(act ** 2)) + 1e-12)
    g = min(g, 10 ** (-3 / 20) / np.max(np.abs(seg)))
    seg *= g
    with wave.open(os.path.join(out, f'write_loop_{k + 1}_{p}.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(seg, -1, 1) * 32767).astype(np.int16).tobytes())
print('ok')
