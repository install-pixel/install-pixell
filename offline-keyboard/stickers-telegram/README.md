# Lingo for Telegram

Telegram turns a GIF sent from a keyboard into a video, and video has no transparency, so the OffKey
stickers arrive on a white square. These are the same 18 animated stickers as Telegram **video stickers**
(512 px WebM VP9 with a transparent background, under 3 s and 256 KB): add them once as your own pack and
send them from Telegram's sticker panel, animated and transparent.

1. Download the `.webm` files of `boy/` and/or `girl/` to the phone (open a file on GitHub, then "Download raw file").
2. In Telegram open the **@Stickers** bot and send `/newvideo`.
3. Send a pack name (for example "Lingo"), then for each sticker: the `.webm` file **as a file**
   (📎 → File), then one emoji for it.
4. Send `/publish`, skip the icon (`/skip`), and pick a short link name. The pack is now in your stickers.

Made by `python3 tools/stickers/animate_sheets.py --telegram` from the sheets in `tools/stickers/source`.
