# Results — TCL BeyondTV2 / R851T02 V660 (2026-10-18/19)

Method: [../METHODOLOGY.md](../METHODOLOGY.md). CPU % = share of one core, system-wide.

> **Every number below is tagged with the launcher version it was measured on.**
> Numbers are never carried over from one version to the next. If a version has no row, it was not measured.
> The chart in the README mixes the v0.3.3 launcher rows with stock-Home app-launch rows; this file is the
> authoritative reference.

## Stock Home vs light launcher (`tcl-bench.sh`, 3 reps each)

| Run | Home | Idle CPU | MemAvailable | Jank % | p90 / p99 frame | Idle fps |
|---|---|---|---|---|---|---|
| baseline | stock `tvlauncher` | 53.0% | 448 MB | 87.6% | 46 / 105 ms | — |
| after 3 TCL packages disabled | stock | 59.3% | 384 MB | 86.3% | 42 / 73 ms | — |
| stock reference (same session as below) | stock | 57.3% | 427 MB | 85.9% | 48 / 150 ms | 60 |
| launcher v0.1 | light | **15.5%** | **528 MB** | **7.7%** | 13 / 24 ms | **0.1** |

Disabling the TCL packages gave **no measurable** gain (differences within run-to-run noise).

## Launcher versions (`tcl-launcher-bench.sh`, 36 keys × 10 rounds)

| Version | Key response p50 / p95 / p99 / max | Full move p50 / max | Janky frames | Back to Home p50 / max | PSS |
|---|---|---|---|---|---|
| v0.2 | 13 / 16 / – / 19 ms | 641 / 749 ms | 9.2% | 56 / 206 ms | 29 MB |
| v0.3.3 normal | 14 / 19 / – / 20 ms | 641 / 696 ms | 16.2% | 39 / 236 ms | 20 MB |
| v0.3.3 ultralight | 16 / 29 / – / 34 ms | **17 / 41 ms** | **0%** | 36 / 191 ms | 20 MB |
| **v0.5.2 (current)** | **13 / 16 / 17 / 17 ms** | 631 / 645 ms | **0.4%** | 39 / 181 ms | 23 MB |

Cold start: v0.3 (WaitTime, 5 runs) 470–551 ms, p50 514 ms · **v0.5.2** (am start -W, 5 runs) 414–483 ms, p50 414 ms.

### v0.5.2 — full run, 2026-10-19 12:20

Package `br.eddy.tvhome`, versionName `0.5.2`, the build installed on the device.

| Metric | Value |
|---|---|
| Key response (key event → first reacting frame) | p50 **13 ms**, p95 16 ms, p99 17 ms, max 17 ms (36 keys) |
| Full move (key event → last frame of the movement) | p50 631 ms, max 645 ms |
| Janky frames | 0.4 % of 1367 |
| Cold start (Home) | p50 414 ms, max 483 ms (5 runs) |
| Back to Home | p50 39 ms, p95 181 ms, max 181 ms (10 runs) |
| Idle 60 s | total CPU 14.9 % of one core, **launcher 0 %**, 0.0 fps |
| PSS | 23 MB (21 MB after 10 Home returns) |

The launcher contributes **0 % CPU and 0 frames per second while the screen is idle**: it does not
poll, does not animate continuously, and has no background service. The 14.9 % total is the rest of
Android (see the idle-CPU findings in the device profile).

Raw output of this run: `medicoes/launcher/20261019-122030-v0.5-atual/` (local only, git-ignored).

## v0.4 / v0.5 status

v0.4 (identity, on-TV settings, HDMI number keys, recovery action) and v0.5 (Globoplay button redirect
via window event) are **installed and running** on the device, pass the smoke test and the reboot
test, and v0.5.2 has now been measured with the full battery above. The earlier v0.3.3 rows are kept
so the version history stays visible; they are not reused as v0.5 numbers.

## Reboot (`tcl-reboot-test.sh`)

| Run | `adb reboot` → boot_completed | Home focused | Home drawn |
|---|---|---|---|
| stock Home | 43 s | 44 s | 3.8 s |
| light Home | 40 s | 40 s | 3.1 s |

## Smoke test (24 checks) — 2026-10-19 12:20

24/24 OK, no crashes or ANRs. Two checks were flaky before a fix and are now stable:
`Home abre` (the focus needs more than 3 s to settle after the Home key) and the three voice checks
(the assistant needs ~10 s from `KEYCODE_SEARCH` to open the mic and reach the server, not 7 s).
`scripts/tcl-smoke.sh` now polls for those events instead of sleeping a fixed time.

## Not measured yet

Standby resume, power-loss boot with stopwatch, Netflix/YouTube/Globoplay time-to-usable,
accessibility-service key latency, 8 h stability, weeks-long daily use of the debloat list.
