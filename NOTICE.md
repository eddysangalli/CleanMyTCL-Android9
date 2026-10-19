# NOTICE — credits and third-party components

CleanMyTCL-Android9 is a **fork** of [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
(formerly `tv-vibehack`) by **Gathin**, MIT License. See [`LICENSE`](LICENSE).

| Component | Origin | Author(s) | License | Where |
|---|---|---|---|---|
| Launcher base (`com.gathin.tvhome.*`), `tools/`, `examples/` | CleanYourTV / tv-vibehack, commit `9ef40c1` (2026-10-18) | Gathin | MIT | `launcher/`, `tools/`, `examples/` |
| Off-main-thread app loading, tile cache, package events | tv-vibehack PR #39 (open upstream when applied) | @leovvay | MIT (contribution to the MIT repo) | `launcher/.../AppLoader.java`, `MainActivity.java` |
| D-pad focus stays inside a row at its ends | tv-vibehack PR #55 (open upstream when applied) | @yashug | MIT (contribution to the MIT repo) | `launcher/.../MainActivity.java` |
| Release build / R8 ideas | tv-vibehack PR #40 (ideas, re-implemented) | @leovvay | MIT | `launcher/app/build.gradle` |
| Key-latency benchmarking approach (per-key p50/p95/p99/max) | `perf.py` idea from a fork by briansilah (methodology only, no code copied) | briansilah | n/a | `scripts/tcl-launcher-bench.sh` |
| Outfit font | Google Fonts / Outfit project | The Outfit Project Authors | SIL OFL 1.1 | `launcher/app/src/main/res/font/outfit.ttf`, `launcher/FONT-LICENSE-OFL.txt` |
| Weather data (optional, off by default) | [Open-Meteo](https://open-meteo.com/) API | Open-Meteo | Data: CC BY 4.0 (attribution required) | `launcher/.../Weather.java` |
| TCL scripts (`scripts/tcl-*`), device profile, docs, benchmarks | Written for this project | Eddy Sangalli and contributors | MIT | `scripts/`, `devices/`, `docs/`, `benchmarks/` |
| TCL launcher features (voice button, menus, ultralight mode, identity, HDMI keys, Globoplay redirect) | Written for this project on top of the fork | Eddy Sangalli and contributors | MIT | `launcher/` (commits after `9ef40c1`) |

Code from the other launchers studied (BareLauncher, lightweight_androidtv_launcher, just-a-launcher,
LtvLauncher, Arc Launcher, PortalLauncher, tvboxtools) was used **only as reference for ideas**;
no code from them is included. Before copying any code from another project, check its license
and add a row here (see [`docs/PROVENANCE.md`](docs/PROVENANCE.md)).

Trademarks (TCL, Android TV, Google, Netflix, YouTube, Globoplay, Spotify) belong to their owners.
This project is not affiliated with or endorsed by TCL or Google.
