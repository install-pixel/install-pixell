"""Synthesizes the Black Typewriter key sounds into res/raw (no recordings, so no licences).

    python3 tools/sounds/make_typewriter_sounds.py app/src/main/res/raw
"""
import sys, wave
import numpy as np

RATE = 22050
out = sys.argv[1]
rng = np.random.default_rng(7)

def t(sec): return np.arange(int(RATE * sec)) / RATE

def env(x, attack, decay):
    a = np.minimum(1, x / attack)
    return a * np.exp(-x / decay)

def write(name, sig):
    sig = sig / max(1e-9, np.max(np.abs(sig))) * 0.85
    data = (sig * 32767).astype(np.int16)
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(data.tobytes())

def strike(pitch, body, length=0.09, seed=0):
    r = np.random.default_rng(seed)
    x = t(length)
    noise = r.standard_normal(len(x)) * env(x, 0.0004, 0.004)          # the type hitting the paper
    metal = (np.sin(2*np.pi*pitch*x) + 0.6*np.sin(2*np.pi*pitch*1.53*x) + 0.35*np.sin(2*np.pi*pitch*2.71*x)) * env(x, 0.0008, 0.018)
    thump = np.sin(2*np.pi*body*x) * env(x, 0.001, 0.025)              # the key bottoming out
    # A tiny second tap as the typebar falls back.
    tap = np.zeros_like(x)
    k = int(RATE * 0.035)
    tap[k:] = r.standard_normal(len(x)-k) * env(x[:len(x)-k], 0.0003, 0.003) * 0.35
    return 0.9*noise + 0.35*metal + 0.5*thump + tap

for i, (p, b) in enumerate([(2300, 170), (2550, 185), (2100, 160)]):
    write(f"tw_key_{i+1}", strike(p, b, seed=i+1))
write("tw_space", strike(1400, 110, 0.12, seed=9) * np.concatenate([np.ones(10), np.ones(int(RATE*0.12)-10)]))

# Carriage return: a fast ratchet sliding back, then the bell.
x = t(0.75)
ratchet = np.zeros_like(x)
for j in range(14):
    s = int(RATE * (0.02 + j * 0.018))
    n = int(RATE * 0.006)
    ratchet[s:s+n] += rng.standard_normal(n) * np.exp(-np.arange(n)/(RATE*0.0015)) * (0.6 + 0.4*np.sin(j))
b0 = int(RATE * 0.30)
bx = x[:len(x)-b0]
bell = (np.sin(2*np.pi*2350*bx) + 0.5*np.sin(2*np.pi*5600*bx) + 0.25*np.sin(2*np.pi*7900*bx)) * env(bx, 0.001, 0.22)
sig = ratchet * 0.8
sig[b0:] += bell * 0.7
write("tw_return", sig)
print("ok")
