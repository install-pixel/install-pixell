"""Builds the Black Typewriter sounds in res/raw from two recordings of a real typewriter chosen by the owner
(single keystrokes, and typing with carriage returns and the bell). The recordings are not in the repository.

    python3 tools/sounds/make_typewriter_sounds.py keys.wav typing.wav app/src/main/res/raw

Both inputs: mono 44.1 kHz WAV (ffmpeg -i in.mp3 -ac 1 -ar 44100 out.wav). Keys and the space bar are cut
from the single keystrokes; Enter is a carriage return cut from the typing; the margin bell is made from the
bell's own two partials measured in the typing (it always rings over other keys there); a new sheet is a soft
ratchet of the platen knob made from the same keystrokes, slowed down.
"""
import sys, wave
import numpy as np

keys_path, typing_path, out = sys.argv[1:4]

def read(path):
    with wave.open(path) as w:
        rate = w.getframerate()
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    return rate, x

def write(name, sig, rate, peak=0.97, drive=3.0):
    # Loud enough at full volume: a soft saturation lifts the body of each strike, not only its peak.
    sig = sig / max(1e-9, np.max(np.abs(sig)))
    sig = np.tanh(drive * sig) / np.tanh(drive) * peak
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes((sig * 32767).astype(np.int16).tobytes())

def cut(x, rate, start, length, fade=0.02):
    """[length] seconds from just before the first attack after [start], with a short fade in and out."""
    a = int(start * rate)
    window = x[a:a + int(0.05 * rate)]
    onset = a + int(np.argmax(np.abs(window) > 0.25 * np.max(np.abs(window)))) - int(0.002 * rate)
    seg = x[onset:onset + int(length * rate)].copy()
    n_in = int(0.001 * rate)
    seg[:n_in] *= np.linspace(0, 1, n_in)
    n_out = int(fade * rate)
    seg[-n_out:] *= np.linspace(1, 0, n_out) ** 2
    return seg

rate, keys = read(keys_path)
# Strikes of an even loudness and length, so fast typing never jumps out.
for i, at in enumerate([2.76, 3.36, 6.78, 12.62, 14.14]):
    write(f"tw_key_{i + 1}", cut(keys, rate, at, 0.12), rate)
# The space bar: the darkest of the strikes, a little longer.
write("tw_space", cut(keys, rate, 16.76, 0.14), rate)

trate, typing = read(typing_path)
# Carriage return: the lever, the carriage sliding back over its ratchet, and the stop.
write("tw_return", cut(typing, trate, 40.86, 0.74, fade=0.08), trate)

# The margin bell: its partials as measured (3216 and 3300 Hz beating, and 8130 Hz).
t = np.arange(int(0.9 * trate)) / trate
ring = np.exp(-t / 0.3) * np.minimum(1, t / 0.0008)
bell = (np.sin(2 * np.pi * 3216 * t) + 0.7 * np.sin(2 * np.pi * 3300 * t) + 0.3 * np.sin(2 * np.pi * 8130 * t) * np.exp(-t / 0.12)) * ring
write("tw_bell", bell, trate, peak=0.8, drive=1.0)

# A new sheet: the platen knob turning, a soft ratchet of slowed-down strikes.
click = cut(keys, rate, 6.78, 0.06)
slow = np.interp(np.arange(0, len(click), 0.6), np.arange(len(click)), click)
sheet = np.zeros(int(0.75 * rate))
for j in range(8):
    s = int((0.04 + j * 0.08) * rate)
    sheet[s:s + len(slow)] += slow[:len(sheet) - s] * (0.55 + 0.1 * (j % 3))
write("tw_sheet", sheet, rate, peak=0.7, drive=2.0)
print("ok")
