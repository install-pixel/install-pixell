"""
The sounds of the sheet (pencil) style, from the owner's recordings (not kept in the repository):
a pencil writing (strokes, short and long) and a rubber eraser (various strokes).

What was done, in order (see the session notes of keyboard 1.16.0):
1. Tone: highs rolled off (pencil ~2.4 kHz, eraser ~1.5 kHz), nothing below ~260 Hz (pencil) / ~150 Hz
   (eraser): the recording has a desk hum near 190 Hz that made fast typing buzz; warmth from a gentle
   boost of 300-900 Hz instead. Filtering on a zero-padded copy, so no tail wraps onto the start.
2. Keys (sh_key_01..12): the pencil recording slowed to 0.8; 100 ms grains from where the lead is on the
   paper, six to a letter, spread evenly; one slow rise and a long fade (~0.3 s), so letters typed
   quickly flow into one line. Twelve different ones, never the same twice in a row.
3. Space (sh_space): only the soft tap of the lead touching the paper (nothing is drawn).
4. Return (sh_return): a long stroke and a dot.
5. Delete (sh_erase) and held delete (sh_erase_hold, ~1.4 s, looped): the eraser slowed to 0.82, its quick
   flutter evened out, soft edges.
Levels by overall loudness (no compression), a plain ceiling at 0.85.
6. 1.17.0, louder on a phone's speaker (the owner heard the pencil far quieter than the typewriter, and the
   eraser hardly at all): above 1.5 kHz, where the speaker and the ear are, the keys were ~9 dB under the
   typewriter's, the eraser ~15 dB, the held eraser ~18 dB. With ffmpeg:
   equalizer=f=2200:t=o:w=1.4:g=5,volume=4dB (keys, return), f=2000 g=4 +5 dB (space),
   f=1800 g=7 +7 dB (delete) / +9 dB (held delete, now played at full volume), then
   alimiter=limit=0.89. The keys now match the typewriter there; the eraser is ~4 dB under it.
7. 1.18.0, the owner: louder still, every fourth letter "a hit, like tape run backwards", the held eraser
   hardly heard. The keys were rebuilt from the 1.16 files (before 1.17's lift): slowed to 0.85 (longer,
   same pitch), cut to start where the lead is already on the paper (no slow swell) with a 5 ms fade-in,
   full for 40 ms and then a long smooth tail to 0.34 s; the strongest grains evened out (a soft knee at
   45 % of the peak); then equalizer=f=2500:t=o:w=1.3:g=6, each key's gain set so that above 1.5 kHz it
   sits near the typewriter (-19 dB RMS peak, ±1.7 dB), alimiter=limit=0.89. Return the same way without
   the reshaping; delete f=2000 g=11 +11 dB, held delete f=2000 g=11 +13 dB (~4 dB over 1.17); space +9 dB.
"""
