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
| Background | Home options → wallpaper, Left/Right to step: black, six dark solid colours (≥ 13:1 contrast with the white text), Aurora, Earth at night (NASA, public domain) | Colours: one full-screen fill. Photos: +9 MB PSS and a measured jank cost during the spring (0.19% with black → 6.89% with aurora); ultralight always stays black. [A/B](../benchmarks/results/2026-10-19-v0.5.6-wallpaper.md) |
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

## Known gaps before public release

- `default_name` in `launcher/app/src/main/res/values*/strings.xml` is still `Eddy` (only shown
  while no name is set). Public builds need a neutral default.
- `HomePrefs.DEFAULT_REMAP` points to a personal app; public default must be "off".
- `applicationId` is `br.eddy.tvhome`; a public id (e.g. `io.github.cleanmytcl.home`) would be a
  separate install from the owner's.
- Open-Meteo requires attribution (CC BY 4.0) when weather is shown.
