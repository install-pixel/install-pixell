"""Final bubble set from the owner's picks among the build3 candidates (out3): keys 01-07, all four spaces,
Return made of the chosen key pops (three, or two, quickly in a row), Backspace 03 and 04, and the held
Backspace as those two puffs in turn, looped. Usage: build3b.py <out3> <res_raw>"""
import os, sys, glob, wave
import numpy as np
SR = 44100
src, raw = sys.argv[1], sys.argv[2]
def read(prefix):
    with wave.open(glob.glob(os.path.join(src, prefix + '*.wav'))[0]) as w:
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768
def save(name, x):
    with wave.open(os.path.join(raw, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
def peak(x, db): return x * (10 ** (db / 20) / (np.max(np.abs(x)) + 1e-12))
keys = [read(f'key_{i:02d}') for i in range(1, 8)]
for i, k in enumerate(keys): save(f'gl_key_{i + 1:02d}', k)
for i in range(1, 5): save(f'gl_space_{i}', read(f'space_{i:02d}'))
for n, (combo, times) in enumerate([((0, 2, 4), (0, 0.09, 0.19)), ((1, 3, 5), (0, 0.085, 0.18)), ((6, 0), (0, 0.11))]):
    ret = np.zeros(int(0.42 * SR))
    for j, (k, at) in enumerate(zip(combo, times)):
        g = peak(keys[k], -5 - j * 0.6); s = int(at * SR)
        ret[s:s + len(g)] += g[:len(ret) - s]
    save(f'gl_return_{n + 1}', ret)
d = [read('delete_03'), read('delete_04')]
for i, x in enumerate(d): save(f'gl_delete_{i + 1}', x)
# Held: the two puffs in turn, a little uneven in time and level, wrapped round so the loop has no seam.
L = int(1.36 * SR); hold = np.zeros(L)
for j, (gap, gain) in enumerate(zip([0, 0.165, 0.345, 0.51, 0.68, 0.855, 1.02, 1.19], [0, -1.5, -0.7, -2, -0.3, -1.2, -0.9, -1.8])):
    x = d[j % 2] * 10 ** (gain / 20); s = int(gap * SR)
    for t in range(len(x)): hold[(s + t) % L] += x[t]
save('gl_delete_hold', hold)
print('ok')
