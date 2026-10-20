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
| Experience preset (Performance / Balanced / Visual / Custom) | Home options → Experience | Performance is the default for new installs. An upgrade keeps the old motion/wallpaper values and shows **Custom** until a preset is chosen. See [EXPERIENCE-PRESETS.md](EXPERIENCE-PRESETS.md) |
| Performance preset (formerly ultralight; no motion, focus in 1 frame) | Home options → Experience or motion | Saves the spring animation (~0.6 s per move); forces the black background and releases the wallpaper bitmap. Not separately benchmarked in v0.5.6 |
| Balanced preset | Home options → Experience | Smooth focus with the current spring and a black background; no wallpaper decode |
| Visual preset | Home options → Experience | Smooth focus + Aurora; measured on the TCL BeyondTV2 with v0.5.6: +9 MB PSS and delayed frames 0.19% → 6.89%, while median key response stayed at 17 ms. [A/B](../benchmarks/results/2026-10-19-v0.5.6-wallpaper.md) |
| Grid columns (4/5/6) | Home options → columns | 0; 4 is the default, and the dock follows the slot count |
| Family profiles | Home options -> profiles | 0; name/message/accent and the background id, no image data or service. Loading one marks the experience as Custom |
| Background (optional) | Home options -> wallpaper, Left/Right to step: black, six dark solid colours (at least 13:1 contrast with the white text), Aurora, Earth at night (NASA, public domain), three BlurHash blurs | Colours: one full-screen fill, not separately measured. Blurs: a 32x18 bitmap decoded from a 28-character string (no bundled image in the APK, stretched full-screen by GPU filtering; **not measured on this device**). Aurora: measured on the TCL BeyondTV2 with v0.5.6, +9 MB PSS and delayed frames 0.19% to 6.89%, median key response 17 ms. Earth at night is **not measured on this device**. Ultralight always stays black. [A/B](../benchmarks/results/2026-10-19-v0.5.6-wallpaper.md) |
| Weather (optional, off) | Home options → city | ≤ 1 request/hour, only while Home is visible |
| Globoplay key redirect | Home options (needs accessibility permission) | Optional accessibility service; it only receives Globoplay window events and starts the chosen app. The target app's own cold start can still take seconds |

**Rules for personalization.** The default experience prioritizes performance: instant navigation,
black background, no continuous graphics. Optional visual features may cost more GPU/RAM or add
measured jank, but they must be opt-in, reversible, and state what is known. Stability, crashes,
ANRs, remote/HDMI/audio/app compatibility and recovery are not optional. Use a measured number only
for the build/device where it was measured; otherwise say **"not measured on this device"**. Never
change packages, firmware or TCL services for a visual option.

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
