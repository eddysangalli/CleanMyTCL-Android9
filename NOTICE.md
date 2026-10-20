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
| Configurable smoke scripts, localized weather, Spanish strings, focus-ring fix | PRs #2, #3, #4, #5 and #9 in this repository (reviewed and merged 2026-10-20) | @roky146 | MIT (contribution to this MIT project) | `scripts/tcl-smoke.sh`, `launcher/.../MainActivity.java`, `launcher/.../Weather.java`, `launcher/app/src/main/res/values-es/` |

Code from the other launchers studied (BareLauncher, lightweight_androidtv_launcher, just-a-launcher,
LtvLauncher, Arc Launcher, PortalLauncher, tvboxtools) was used **only as reference for ideas**;
no code from them is included. Before copying any code from another project, check its license
and add a row here (see [`docs/PROVENANCE.md`](docs/PROVENANCE.md)).

Trademarks (TCL, Android TV, Google, Netflix, YouTube, Globoplay, Spotify) belong to their owners.
This project is not affiliated with or endorsed by TCL or Google.

## Fonts

The Outfit typeface in `launcher/app/src/main/res/font/outfit.ttf` is copyright The Outfit Project
Authors and licensed under the **SIL Open Font License 1.1** — see `launcher/FONT-LICENSE-OFL.txt`.
SIL OFL permits bundling and redistribution inside this project.

## Repository relationship to upstream

This repository is **not** registered as a GitHub "fork" of CleanYourTV, on purpose. It is an
independent project that **preserves CleanYourTV's commit history** (up to `9ef40c1`) and adds
TCL-specific work on top. Registering it as a GitHub fork would either hide the divergence behind
GitHub's fork UI, or make the upstream repository the source of truth for issues and releases.

- Upstream URL: <https://github.com/Gathin23/cleanyourtv>
- Upstream commit this project continues from: `9ef40c1`
- Upstream is tracked locally as the `upstream` remote (push disabled)
- Upstream pull requests #39 and #55 were applied here before being merged upstream; see
  [`docs/PROVENANCE.md`](docs/PROVENANCE.md) for the file-by-file origin
- Upstream credits stay intact in [`launcher/NOTICE.md`](launcher/NOTICE.md)

Contributions that are not TCL-specific should be offered back to upstream; see
[`docs/BRANCHING.md`](docs/BRANCHING.md) for how to do that without forking the workflow.
