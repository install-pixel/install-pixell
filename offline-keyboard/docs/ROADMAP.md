# OffKey roadmap

The style guide for every screen is QRAFT (the family's reference app): two themes, «Скло · Темна» and
«Аркуш · Темна»; later also two light ones (light glass, light sheet) shared by all the apps.

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
