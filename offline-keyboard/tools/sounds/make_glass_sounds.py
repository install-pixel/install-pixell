"""
The sounds of the glass style: bubble wrap popping, from the owner's recordings (not kept in the repository).

What was done (see the session notes of keyboard 1.17.0):
1. 21 single pops cut from the recordings (one file was a copy of another and was dropped).
2. Tone: nothing below ~110 Hz (no hum), narrow ringing tones pressed down so fast typing does not whistle,
   the sharp click above 4 kHz rolled off (73 % of the energy was there, now ~1 %), pitch ~20 % lower,
   warmth from a gentle lift of 250-900 Hz. No compression, a ceiling for peaks, soft edges.
3. Keys (gl_key_01..10): ten different pops, never the same twice in a row, their level left a little uneven
   as real bubble wrap. Space (gl_space): the juiciest pop, lower and deeper. Enter (gl_return): three pops
   in a row, as a finger run along a line of bubbles. Backspace (gl_delete): a soft, dull "pff" of air going
   out. Held Backspace (gl_delete_hold, looped): the wrap rustling, from the quiet between pops.
4. Louder on a phone's speaker, as the pencil (1.17.0): with ffmpeg
   equalizer=f=2200:t=o:w=1.4:g=4,volume=8dB (keys), f=2000 g=4 +7 dB (space), f=2200 g=4 +6 dB (Enter),
   f=2000 g=5 +10 dB (Backspace), f=2000 g=4 +7 dB (held), then alimiter=limit=0.89. Pops are mostly peak,
   so more gain only flattens them: the keys stay ~3-6 dB under the typewriter above 1.5 kHz.
5. 1.18.0: ~2.5 dB more gain on every sound (keys +10.5, space +9, Enter +8, Backspace +12.5, held +9.5),
   the same limiter: about 1 dB louder in the end, as the pops are mostly peak.
"""
