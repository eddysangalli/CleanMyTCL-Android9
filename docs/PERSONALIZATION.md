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
| Ultralight mode (no motion, focus in 1 frame) | Home options (default on) | Saves ~0.6 s of animation per move |
| Weather (optional, off) | Home options → city | ≤ 1 request/hour, only while Home is visible |
| Globoplay key redirect | Home options (needs accessibility permission) | Being measured |

Rules for new personalization features: no continuous animations, no permanent services, no
internet dependency, and a benchmark showing no regression (see [../benchmarks/METHODOLOGY.md](../benchmarks/METHODOLOGY.md)).

## Known gaps before public release

- `default_name` in `launcher/app/src/main/res/values*/strings.xml` is still `Eddy` (only shown
  while no name is set). Public builds need a neutral default.
- `HomePrefs.DEFAULT_REMAP` points to a personal app; public default must be "off".
- `applicationId` is `br.eddy.tvhome`; a public id (e.g. `io.github.cleanmytcl.home`) would be a
  separate install from the owner's.
- Open-Meteo requires attribution (CC BY 4.0) when weather is shown.
