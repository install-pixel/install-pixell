# OffKey roadmap

The style guide for every screen is QRAFT (the family's reference app): two themes, «Скло · Темна» and
«Аркуш · Темна»; later also two light ones (light glass, light sheet) shared by all the apps.

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
- Theme follows the launcher (one theme for all apps) — when the launcher is updated.
- Light themes (light glass, light sheet).

## Not planned

- Handwriting input: no offline recogniser that can be bundled (ML Kit downloads its models itself).
