# Upstream update — v2.3.0, new PRs and forks (2026-10-19)

Follow-up to [`UPSTREAM-AUDIT-2026-10-19.md`](UPSTREAM-AUDIT-2026-10-19.md) (which covers
upstream v2.2.0, PR #119/#150/#153, issue #165/#166 and the v0.5.2 measurement).

Everything below comes from the upstream release/PR pages and the fork owners' code, reviewed on
2026-10-19. **None of it has been measured on our TCL RTD2851 yet.** The measured reference stays
the v0.5.2 battery ([`2026-10-19-v0.5.2.md`](../benchmarks/results/2026-10-19-v0.5.2.md)) until the
v0.5.4 battery runs.

Legend: **ready** = small, low-risk change we can implement behind the existing tests;
**prototype** = needs a before/after measurement on the TV first; **investigate** = needs a design
or availability check; **skip** = do not import.

## 1. Official v2.3.0

The maintainer says the release was built and passed the automated tests but **was not run on a TV
by him**. Treat the hardware behaviour as untested.

| Item | What it is | Our status | Action |
|---|---|---|---|
| GPU-optimised animations + `reduced_motion` (PR #119) | cheaper/shorter focus animation; reduced motion is opt-in upstream | ultralight already removes the motion; normal mode is a ~632 ms spring (measured on v0.5.2) | **prototype**: try to cut the 632 ms in normal mode without losing the look; measure with the A/B harness |
| D-pad focus fix (PR #138) | after moving in the top bar, Down returns to the app the user came from; developed on a TCL BeyondTV (Android 9) | our focus logic is custom (`MainActivity`, `DockDecoration`, `focusDown`) | **prototype**: diff the upstream fix against our handlers; behaviour test with injected keys |
| Installer safety (PR #190) | stop if the APK install fails, before touching the stock launcher | `scripts/tcl-launcher-install.sh` runs `set -euo pipefail`, never swaps the Home and re-checks it after install | **ready (small)**: assert the `adb install` result explicitly |
| Script path fix (PR #153) | resolve the debloat list against the caller's directory | already in `tools/disable.sh` (verified 2026-10-19) | none |

## 2. New and related upstream items

| Item | What it offers | Our status | Action |
|---|---|---|---|
| PR #199 — pt-BR launcher translation (46 strings) | complete Portuguese strings, tested on a Brazilian TCL | our `values-pt-rBR/strings.xml` already covers **68/68** strings | none (compare wording only) |
| PR #137 — TCL BeyondTV EU (RTD2851, Android 9, V606) | voice-service CPU, HDMI input behaviour | matches our finding: `voice_ap` + `katniss` ≈ 52.8% of one core; trimming packages does not remove the native voice path | use as cross-check; never promise zero voice cost |
| PR #181 — TCL 50EP680 (`rtd285o`, Android 11) | same board family, different firmware | different OS major version | **watch** (future) |
| PR #200 — TCL UnionTV (RT2841, Android 9) | debloat list; Quick Start turns Ethernet off in standby; disables voice/a11y services | hardware and firmware differ; the author has not run it for several days | **test only**: check whether our TV also drops Ethernet in standby (standby / 8 h soak); do **not** import the list |
| PR #176 — configurable key remap | user-defined key→app mappings | our `KeyRemapService` already redirects the Globoplay key, which TCL injects (scanCode 0) and a generic key filter cannot see | **skip for Globoplay**; revisit only if generic remapping is requested |
| PR #189 — dock order holding OK | reorder tiles with a long press | we already have tile menu → “move right” and the dock preferences | **compare UI**; do not duplicate |
| PR #173 / bramsuurdje — Now Playing | cover, title and transport controls on the Home | needs a notification listener (permanent service) | **skip for now** (breaks the no-permanent-services rule); revisit only as a measured opt-in module |
| PR #174 — gallery wallpaper | photo wallpaper decoded off the main thread | we have no wallpaper; issue #166 is about full-size decode | **investigate** only with the profiles idea; downsample first |
| PR #167 — columns/motion on the TV | configurable grid columns and animations | our options cover motion/name/accent; columns is fixed at 4 (`res/values/config.xml`) | **prototype** together with the zennnez grid fix |
| PR #175 — ADB controls in the Home | toggle debugging from the launcher | privileged maintenance | **skip** (keep it a separate tool) |
| Issue #165 — package validation | validate names before disabling | already local: `tools/disable.sh` sources `scripts/tcl-pkg-validate.sh` | none |
| Issue #166 — wallpaper decoded full size | memory spike on open | we have no wallpaper; relevant only if profiles/wallpaper are added | **note** for that prototype |
| PRs #179 / #180 | our BeyondTV2 profile and the project in SHOWCASE | merged upstream; SHOWCASE lists this project | none |

## 3. Forks with concrete code

| Fork | What it changes | Fit for our v0.5.4 | Action |
|---|---|---|---|
| [zennnez/tv-vibehack](https://github.com/zennnez/tv-vibehack) | fixes 5/6-column grids: card width follows the available space instead of a fixed width | our `item_app.xml` uses a fixed `@dimen/tile_width` with `GridLayoutManager(COLUMNS)` | **ready (small)**: compute the tile width from the screen; verify 4/5/6 columns with screenshots |
| [Suryvibegod/tv-vibehack-coocaa](https://github.com/Suryvibegod/tv-vibehack-coocaa) | removes a duplicated background; `forceHasOverlappingRendering="false"`; UTF-8 for ADB output on Windows | our `tools/tv.py` already reads `utf-8-sig`; our overdraw was never measured | **prototype**: compare `gfxinfo` overdraw before/after |
| [mhaegeman/cleanyourtv](https://github.com/mhaegeman/cleanyourtv) | profile picker (name + wallpaper) selected with the remote | `HomePrefs` stores name/accent/message; no profiles, no wallpaper | **prototype** (small, no service): profiles as a pref list; wallpaper must be downsampled (#166) |
| [bramsuurdje/tv-vibehack](https://github.com/bramsuurdje/tv-vibehack) | compact Now Playing block | same as PR #173 | **skip for now** |
| [skuhns/unrecommended-tv](https://github.com/skuhns/unrecommended-tv) | live-TV guide, search, favourites; image presets use Android 13+ accessibility APIs | our inputs are `com.tcl.tv`; EPG availability on Android 9 untested | **investigate** EPG via `TvContract`; do not port the Android 13+ automation |
| [mclhm8182/tvhome-zh](https://github.com/mclhm8182/tvhome-zh) | prebuilt APK, wallpaper, 3–6 columns without recompiling | the author reports ~107 MB PSS vs our measured 18 MB | **skip as a base**; take only configuration/distribution ideas |
| [benfirad/tv-vibehack](https://github.com/benfirad/tv-vibehack) | retractable dock, calendar, “Stremio row” | the Stremio row is five titles **hard-coded** in `AppAdapter.java` (Dune: Part Two, Breaking Bad, The Penguin…), with posters downloaded — not real watch history | **skip the feature**; visual reference only |

## 4. What we already have (do not re-port)

Verified in our code while writing this note:

- tiles cached for the process and rebuilt only when the installed set changes
  (`MainActivity.onResume` → `AppLoader.cached()` + `sameTiles`) — this is PR #150's core;
- entry animation only on the first build (`if (first && !light)`);
- ultralight mode **on by default** (`HomePrefs.light()`), changes motion only;
- accessibility service scoped to one package (`key_remap.xml`);
- on-TV settings (name, message, accent, motion, dock, hidden apps, recovery);
- HDMI number keys 1–3 (the three inputs this unit exposes), voice button, signed release;
- `tools/disable.sh` path resolution and package validation (PR #153 + issue #165);
- `tools/tv.py` UTF-8-safe stdin (the Suryvibegod fix is already covered);
- pt-BR strings complete (68/68).

## 5. Recommended order (after the paused tests resume)

1. **Re-baseline v0.5.4** — full battery + normal/ultralight A/B (scripts ready:
   `tcl-launcher-bench.sh`, 5 runs per mode). The v0.5.2 numbers stop being the “current build”
   reference the moment v0.5.4 is measured.
2. **D-pad focus fix (PR #138)** — small, behavioural, testable with injected keys.
3. **Grid width fix (zennnez)** — small, layout-only, testable with screenshots at 4/5/6 columns.
4. **Normal-mode animation cost (v2.3.0 / PR #119)** — the only way to cut the 632 ms without
   turning motion off; prototype it behind the A/B harness.
5. **Installer assert (PR #190)** — one explicit check in `tcl-launcher-install.sh`.
6. **Overdraw trim (Suryvibegod / PR #150)** — small; needs `gfxinfo` overdraw before/after.
7. **Profiles (mhaegeman)** — optional family feature; includes the #166 wallpaper mitigation.
8. **Investigate only**: live-TV EPG (`TvContract`), Now Playing (permission audit first), dock
   reorder (#189) only if it adds something over our tile menu.

Every step: same-session before/after, ≥ 5 repetitions, smoke 24/24, and the physical checks that
need a person (audio, HDMI content, standby with a stopwatch). No step ships without a measured
“no regression” on the launcher battery.

## 6. Risks

- v2.3.0 was not run on a TV by its own maintainer; the fork code was never run on an RTD2851.
- PR #150's branch is stacked on network features (#143 self-update, #149 Bitcoin ticker); import
  only the parts, never the branch.
- PR #200's debloat list is for different hardware/firmware and lacks multi-day testing.
- Now Playing, profiles and wallpapers add services, permissions or memory; they stay opt-in and
  measured, never part of the default Home.
- The Netflix “time to a usable catalogue” and the physical remote latency remain human checks;
  no fork changes that.

## 7. Links

- Release: <https://github.com/Gathin23/cleanyourtv/releases/tag/v2.3.0>
- PRs: [#119](https://github.com/Gathin23/cleanyourtv/pull/119) · [#137](https://github.com/Gathin23/cleanyourtv/pull/137) · [#138](https://github.com/Gathin23/cleanyourtv/pull/138) · [#150](https://github.com/Gathin23/cleanyourtv/pull/150) · [#153](https://github.com/Gathin23/cleanyourtv/pull/153) · [#167](https://github.com/Gathin23/cleanyourtv/pull/167) · [#173](https://github.com/Gathin23/cleanyourtv/pull/173) · [#174](https://github.com/Gathin23/cleanyourtv/pull/174) · [#175](https://github.com/Gathin23/cleanyourtv/pull/175) · [#176](https://github.com/Gathin23/cleanyourtv/pull/176) · [#181](https://github.com/Gathin23/cleanyourtv/pull/181) · [#189](https://github.com/Gathin23/cleanyourtv/pull/189) · [#190](https://github.com/Gathin23/cleanyourtv/pull/190) · [#199](https://github.com/Gathin23/cleanyourtv/pull/199) · [#200](https://github.com/Gathin23/cleanyourtv/pull/200)
- Issues: [#165](https://github.com/Gathin23/cleanyourtv/issues/165) · [#166](https://github.com/Gathin23/cleanyourtv/issues/166)
- Our contributions: [#179](https://github.com/Gathin23/cleanyourtv/pull/179) · [#180](https://github.com/Gathin23/cleanyourtv/pull/180) · [SHOWCASE](https://github.com/Gathin23/cleanyourtv/blob/main/SHOWCASE.md)
- Forks: [mhaegeman](https://github.com/mhaegeman/cleanyourtv) · [zennnez](https://github.com/zennnez/tv-vibehack) · [Suryvibegod](https://github.com/Suryvibegod/tv-vibehack-coocaa) · [bramsuurdje](https://github.com/bramsuurdje/tv-vibehack) · [skuhns](https://github.com/skuhns/unrecommended-tv) · [mclhm8182](https://github.com/mclhm8182/tvhome-zh) · [benfirad](https://github.com/benfirad/tv-vibehack)
