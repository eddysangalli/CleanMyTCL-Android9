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
| Family profiles | Home options → profiles | 0; name/message/accent and a wallpaper on/off flag, no image data or service. Loading one marks the experience as Custom |
| Aurora wallpaper (optional) | Home options → wallpaper | Enabling it switches to Visual because this build only shows the wallpaper with smooth motion; the UI states the v0.5.6 cost before enabling. Black remains the default/Performance background |
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

## Known gaps before public release

- `default_name` in `launcher/app/src/main/res/values*/strings.xml` is still `Eddy` (only shown
  while no name is set). Public builds need a neutral default.
- `HomePrefs.DEFAULT_REMAP` points to a personal app; public default must be "off".
- `applicationId` is `br.eddy.tvhome`; a public id (e.g. `io.github.cleanmytcl.home`) would be a
  separate install from the owner's.
- Open-Meteo requires attribution (CC BY 4.0) when weather is shown.
