# Personalization (public base vs. your own identity)

CleanMyTCL-Android9 is the reusable base. A personal setup such as **"Eddy TV"** is just a
configuration of it. Everything below is set **on the TV** (Home options: menu key, or hold OK on
Settings) and stored in the launcher's private preferences — nothing personal goes into the repo.

| Option | Where | Cost |
|---|---|---|
| User name ("Good morning, <name>.") | Home options → name | 0 (local clock, checked once a minute, text set only when it changes) |
| System name (brand line) | Today derived as "<name> TV" (`brand_format`); a separate setting is **not implemented yet** | 0 |
| Recovery menu (re-enable stock Home, open Settings, reset defaults) | Launcher recovery screen | 0 |
| Accent colour | Home options | 0 |
| Custom message under the greeting | Home options | 0 |
| Dock / favourites, hide apps, order | App menu (menu key on a tile) | 0 |
| HDMI 1/2/3 and Live TV shortcuts | Inputs row; number keys jump to HDMI | 0 |
| Ultralight mode (no motion, focus in 1 frame) | Home options (**code default on**; the v0.5.2 battery TV was left in normal) | Saves ~0.6 s of animation per move; also forces the black background (the wallpaper is a smooth-motion feature). The greeting and weather line are the same in both modes |
| Grid columns (4/5/6) | Home options → columns | 0; 4 is the default, and the dock follows the slot count |
| Family profiles | Home options → profiles | 0; name/message/accent and the background id, no image data or service |
| Background | Home options → wallpaper, Left/Right to step: black, six dark solid colours (≥ 13:1 contrast with the white text), Aurora, Earth at night (NASA, public domain), three BlurHash blurs | Colours: one full-screen fill. Blurs: a 32×18 bitmap decoded from a 28-character string, nothing in the APK. Photos: +9 MB PSS and a measured jank cost during the spring (0.19% with black → 6.89% with aurora); ultralight always stays black. [A/B](../benchmarks/results/2026-10-19-v0.5.6-wallpaper.md) |
| Weather (optional, off) | Home options → city | ≤ 1 request/hour, only while Home is visible |
| Globoplay key redirect | Home options (needs accessibility permission) | Being measured |

Rules for new personalization features: no continuous animations, no permanent services, no
internet dependency, and a benchmark showing no regression (see [../benchmarks/METHODOLOGY.md](../benchmarks/METHODOLOGY.md)). The v0.5.6 static wallpaper is the documented exception: it keeps
key response and spring duration, but it has a measured jank/PSS cost; it is optional and ultralight
always stays black.

> **History note (2026-10-19).** An earlier description of this project (upstream PR #180) said the
> ultralight mode was the default and did not fetch the weather. The first part is correct — the code
> default is `light=true` — but the second is not: the weather line is shown in both modes, and the
> fetch is cached and refreshed at most once an hour. The v0.5.2 battery was measured with smooth
> motion **on** (normal mode), which is why the report's conditions say `light=false`.

## Adding a blurred background

A blur is a [BlurHash](https://github.com/woltapp/blurhash) of a photo: only a ~30-character string goes
into the app, so it costs no APK size. Any photo works, but you must be allowed to use it (public domain,
CC0 or CC BY with credit) and the white header text must stay readable.

1. Start from a landscape photo, at least 1280 px wide. Darker images give the best contrast.
2. Make the hash (Windows PowerShell; the path is an example):
   ```powershell
   powershell -ExecutionPolicy Bypass -Command "& .\scripts\tcl-blurhash.ps1 -Images @('C:\photos\mine.jpg')"
   ```
   The script crops the centre to 16:9, darkens the top 45% where the header sits (the same gradient as
   the bundled photo) and prints `<file> <hash>` (4×3 components, 28 characters).
3. In `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`, add one entry at the end of each
   array, all at the same position: an id to `BG_IDS` (never reuse or rename one: it is what the TV saves),
   `0` to `BG_COLORS` and `BG_IMAGES`, and the hash to `BG_HASHES`.
4. Add its name at the same position in `<string-array name="wallpaper_names">` in every
   `res/values*/strings.xml` that has the list (a missing name shows the id instead of crashing).
5. Credit the photo in `launcher/NOTICE.md` (title, author, license, link), build, and check it on the TV
   with a long greeting and message: white text must stay readable over every part of the header.

## Known gaps before public release

- `default_name` in `launcher/app/src/main/res/values*/strings.xml` is still `Eddy` (only shown
  while no name is set). Public builds need a neutral default.
- `HomePrefs.DEFAULT_REMAP` points to a personal app; public default must be "off".
- `applicationId` is `br.eddy.tvhome`; a public id (e.g. `io.github.cleanmytcl.home`) would be a
  separate install from the owner's.
- Open-Meteo requires attribution (CC BY 4.0) when weather is shown.
