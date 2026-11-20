# OffKey roadmap

The style guide for every screen is QRAFT (the family's reference app): two themes, «Скло · Темна» and
«Аркуш · Темна»; later also two light ones (light glass, light sheet) shared by all the apps.

## 1.10.0 — prediction from two words, the keyboard learns the finger, fixes

- [x] Next-word prediction from the two previous words (Tatoeba word triples and the user's own); a switch.
- [x] The keyboard learns where the finger lands on each letter and moves the borders between keys; a
      switch and «Forget how I tap».
- [x] The strip shows the whole message, the part after the cursor dimmer: every word can be tapped
      (cursor there) or held (selected), both before and after the cursor.
- [x] Space-bar cursor goes both ways in long texts, and keeps going when Shift turns on.
- [x] Choosing mobile or computer keyboard in the settings works (also with «remember the last mode»).
- [x] The test field and the glossary are fixed windows that scroll inside.
- [x] The dictionary shows every word (scrolls inside), to find and delete a word typed by mistake.
- [x] No Ukrainian words in the Polish or English list and no Latin words in the Ukrainian one.
- [x] Louder typewriter sounds at full volume.

## 1.11.2 — levers that move, paper edges

- [x] Every lever has a collar just above its key that goes down with the key; a pressed key's lever
      catches the light and its type strikes up under the paper.
- [x] The soft top edge of the keyboard fades into the black paper (no line); the paper's lower edge is a
      lighter lip with its shadow on the machine (no lever ends showing).

## 1.11.1 — fix

- [x] No crash when the strip's text changes quickly (dictation with translation): the delayed scroll to
      the cursor checks the text shown at that moment.
- [x] The app shows its real version again (it showed 1.8.0).

## 1.11.0 — the typewriter: paper, straight levers, one machine

- [x] The strip lies on black, slightly crumpled charcoal paper (typewriter only).
- [x] Straight key levers from every key up into the machine; a key goes 4.5 dp down and springs back with
      a dying swing and a light tick.
- [x] Chrome rims (bevel band, bright lip, sharper light); rust on about one key in six; the most used
      letters a little worn (fine scratches, worn paint).
- [x] The enlarged key is the same key: the same rust and scratches.
- [x] Shift, Backspace, Enter, the layer and language keys bigger; letters as big as the row allows.
- [x] Switching layouts slides: away from the letters to the right, back to the left, the language key
      always round one way, a swipe its own way.
- [x] Lower key sounds: highs rolled off, more body, a little slower; loud without distortion.

## 1.12.0 — first setup, dictionary waits for a second time, letters on the folds

- [x] Dictionary: a word typed once is kept but not suggested until it is typed again (known words right
      away); such words are dimmer in the list, «not suggested yet».
- [x] First setup (opened from the launcher): «Recommended» (the phone's languages, suggestions,
      corrections, a learning dictionary) / «Minimum» / «I'll set it up» (languages, help, sound); turning
      the keyboard on; the style. Again from About. No voice or translation model yet: the message offers it
      (a tap opens the models page).
- [x] Typewriter: the paper under the text moves with it, and letters on a fold of the paper bend with it;
      no lever ends showing on the paper.

## 1.14.0 — Milky Typewriter, paper holder

- [x] New style «Milky Typewriter»: the same machine in light colours — milk-white enamel keys in bronze
      rims, brown letters, a light body with brown shadows, no rust; the strip is old parchment like an
      explorer's map (browned edges, stains, fibres, creases) with the text in brown ink.
- [x] Typewriter: a polished paper holder across the top of the keys, where the paper goes in.
- [x] Typewriter: a pressed key no longer flashes bright above the top row; only a soft glint just above
      the key.
- [x] Nothing typed in the keyboard's own settings (the test field) is learned: words, pairs or taps.

## 1.13.0 — computer remote, sound button

- [x] Computer remote over Bluetooth (first version): a touchpad (tap = click, two fingers = right click
      and scrolling, double tap and hold = drag), left and right buttons, Esc, Tab, Win, Alt+Tab, arrows;
      ♪ swaps in media keys (previous, play/pause, next, volume, mute) and slides ◀ ▶. Upright it takes
      the place of the keys, in landscape it sits beside them. The button shows while typing on the
      computer. A computer paired before may need to pair again for the mouse.
- [x] Sound button in the strip (styles with sounds — the typewriter for now): a tap turns key sounds on or
      off, held the strip becomes a volume bar under the finger.
- [x] The typewriter's end-of-line bell can be turned off (Typing settings).
- [x] Typewriter: no letter bubble over a pressed key by default (can be turned back on).
- [x] The first key sounds no longer come late: sounds load with the keyboard and the audio is woken up.

## Planned

- [ ] Remote, second version: several computers to switch between, connecting by itself.
- [ ] Key sounds for the other styles (the sound button then shows in every style).
- [ ] Data guard: a gentle warning when a card number, IBAN, PESEL or a password-like text goes into a chat.
- [ ] Hide numbers with stars (**** 1234) — optional.

## 1.9.0 — the typewriter to the end, apostrophe, touchable text

- [x] Key levers like the machine: each runs through the gap between the keys below and fans in towards a
      fulcrum; a pressed key pulls its lever down; a rivet on the bend.
- [x] Enlarged letter: a bigger round key; long-press choices: round keys on a steel bar.
- [x] Sounds from the owner's recordings of a real typewriter; the margin bell eight characters before the
      end of a 65-character line; Enter is the carriage return; a new sheet every 30 lines.
- [x] Ukrainian apostrophe put back automatically where a known word has one (мясо → м'ясо).
- [x] The text in the strip: tap a word to put the cursor there (its suggestions replace it), hold to
      select it; tap elsewhere in the strip shows the buttons.

## 1.8.1 — a real typewriter

- [x] Black Typewriter keys like the machine: a nickel rim with a line of light, rust only on the rim,
      a glossy black cap with a soft reflection, a shadow under each key; a pressed key goes down.
- [x] The machine under the keys: a polished stem from every key through its guide, a collar under
      the key; stem and collar go down with the key. No grid of rails.
- [x] A key goes 3 dp down and springs back up with a small overshoot.
- [x] All round keys one size (the edge keys are no longer bigger); labels and digits stay inside;
      only single letters tilt (long words stay straight).

## 1.8.0 — predictions, space cursor, smarter autocorrect, clipboard history, typewriter sounds

- [x] Next-word prediction after a finished word: shipped pairs (Tatoeba, CC BY 2.0 FR) and the user's
      own (learned only while learning is on), shown at the right of the strip beside the text.
- [x] Hold the space bar and slide: the cursor follows (a quick swipe still changes the language).
- [x] Autocorrect weighs slips (two in longer words), frequency and the previous word; it corrects only
      a clear winner.
- [x] Clipboard history: the last copied texts for an hour, pinned ones stay; never passwords.
- [x] Typewriter key sounds (synthesized) with a volume slider; silent on silent/vibrate.

## 1.7.0 — Black Typewriter, punctuation after a picked word

- [x] A picked suggestion's space counts as the first of two: one more space makes ". "; a mark typed
      next moves before the space.
- [x] Third style «Black Typewriter» (the others stay): round keys of an old typewriter with a worn
      nickel rim, scratches, a rubbed middle and a few rust spots (different on every key), a brass rim
      for chosen keys, Cousine letters slightly off their line like struck through a ribbon; the text
      strip strikes each new letter with a small jolt; settings as worn metal plates.

## 1.6.9 — own words in autocorrect

- [x] Words in «My dictionary» (added, imported, or typed often) are never «corrected» away, and a slip in
      a word added by hand or imported is fixed to it before any base word.

## 1.6.8 — the same scrolling everywhere

- [x] Every scrolling list fades at its edges: the add-shortcut dialog (also springs) and the sticker
      panel on the keyboard.

## 1.6.7 — «Back» in its own strip

- [x] «Back» sits in a strip at the very bottom, as in QRAFT; the page scrolls only above it, so nothing
      lies under the button and a switch can't be hit instead of it.

## 1.6.6 — «Back» at the bottom, as in QRAFT

- [x] Every settings page has «Back» at the bottom right (one hand, thumb), fixed while the page
      scrolls; hidden while the keyboard is open. The title stays at the top.

## 1.6.5 — «How to use» exactly as in QRAFT

- [x] Sections listed with a small line icon and the name only (no subtitles), as QRAFT's menu rows.
- [x] Each section is short points «• action — what happens», in the QRAFT voice.
- [x] «Style», never «theme», in every text.

## 1.6.4 — fixes from the 1.6.3 review

- [x] Arrows no longer select text at a sentence start (the automatic capital Shift is for letters only).
- [x] Sheet theme: switching a setting draws the pencil line through (or rubs it out) without a tap halo.
- [x] Modes row: the modes that are not chosen are written dimmer, so the chosen one stands out.
- [x] «How to use» as in QRAFT: first in the settings menu, topics (first steps, typing and gestures,
      autocorrect, punctuation, numbers, computer keyboard, voice and translation, Bluetooth, emoji,
      dictionary and backup), each on its own page.
- [x] Style names as in QRAFT: Black Glass and Black Sketch, each with a line of description.

## 1.6.3 — fixes from the 1.6.2 review

- [x] The Bluetooth note says plainly that translation, voice and suggestions pause only while keys go
      to the computer (the computer layout on the phone has them all).
- [x] Dictionary: the word list is a small window scrolled inside, so import and export stay close.
- [x] Pencil frames keep round corners however tall they get.
- [x] Settings pages spring a little at both ends, also when shorter than the screen.
- [x] A held Backspace stops vibrating once there is nothing left to delete.
- [x] Texts: suggestions come from about 30 000 words per language (not 350).
- [x] Mobile keyboard: the top bar buttons hide until the text strip is tapped (Look → Keys can turn it off).

## 1.6.2 — fixes from the 1.6.1 review

- [x] Landscape: the keyboard's black reaches under the camera cutout; only the keys keep clear of it.
- [x] A held Backspace (or arrow) gives a light tick for every character, not only the first.
- [x] "word?" / "word!" with no space yet: the next letter starts a sentence and gets its capital.
- [x] Number pad operators are plain + - * / with no long-press options, so a slightly long tap still
      types the operator (calculators missed ± and the Unicode minus); the point keeps "." when held.

## 1.6.1 — number pad

- [x] Only ABC (bottom left) leaves the pad; no language key and no ?123 there.
- [x] All four operators: + − × on the left, / top right.
- [x] Calculator layout: 0 double wide, the decimal point "." beside it for every language ("," on a long
      press), because number fields of many apps accept only the dot.
- [x] The cursor keys row is switched on the «Look» page (Keys), not among the computer-mode settings.
- [x] Every layer of both modes is as tall as the mobile letters: no jump when switching computer ↔
      mobile, emoji or number pad.

## 1.6.0 — polish from the 1.5.0 review

- [x] «Про застосунок»: a real description of the app (what it does), then privacy, version, reset.
- [x] Neutral texts everywhere: no personal or work-specific examples (Bluetooth note without the «ы»
      example; translation glossary without warehouse terms).
- [x] No links to the project's GitHub anywhere: models are downloaded from the listed sources or added
      from a file; no «more presets on GitHub».
- [x] Settings hide the status bar (time, network), as in the other apps; a swipe from the top shows it.
- [x] Import / export: own dictionary from/to a file (.txt one word per line, or JSON by language), and
      a full backup of settings, dictionary, translation glossary, own emoji and shortcuts.
- [x] Medium vibration clearly stronger than light.
- [x] One test field (main page), with sentence capitals and all typing rules; placeholder inside the
      field instead of a label that cuts the frame.
- [x] Number pad: Backspace and Enter in the same places as on the letters.
- [x] The system Back gesture does not fire over the keyboard (Backspace swipe works at the edge).
- [x] «?123» symbols: every row as wide as the keyboard, no empty edges.
- [x] Settings: content fades at the top and bottom while scrolling (as in the launcher); highest
      refresh rate (120 Hz where the screen has it).
- [x] Soft top edge of the keyboard (fades into black instead of a hard line), also in settings.
- [x] Bigger suggestion dictionaries: 30 000 words per language from open frequency lists.

## Later, maybe

- Swipe (glide) typing: possible offline, needs large dictionaries; not planned yet.
- Typing in several languages without switching (Polish/English share the Latin keys).
- Emoji search by word (in the emoji panel, and an emoji among the suggestions).
- Key sounds for the other styles; a look at Black Sketch.
- Theme follows the launcher (one theme for all apps) — when the launcher is updated.
- Light themes (light glass, light sheet).
- More «rare things» styles for the shared family theme (after the typewriter).

## Not planned

- Handwriting input: no offline recogniser that can be bundled (ML Kit downloads its models itself).
- Spell checker service for all apps: Android would hand the text of other apps to it; OffKey keeps text inside the keyboard.
