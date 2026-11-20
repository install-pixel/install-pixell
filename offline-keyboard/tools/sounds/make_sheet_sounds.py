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
"""
