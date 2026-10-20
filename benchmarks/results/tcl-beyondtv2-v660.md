# Results — TCL BeyondTV2 / R851T02 V660 (2026-10-18/19)

Method: [../METHODOLOGY.md](../METHODOLOGY.md). System-wide CPU is the share of the **whole
4-core SoC**; the per-process rows in the summaries are the share of **one core**.

> **Every number below is tagged with the launcher version it was measured on.**
> Numbers are never carried over from one version to the next. If a version has no row, it was not measured.
> The detailed 2026-10-19 v0.5.2 battery is in [2026-10-19-v0.5.2.md](2026-10-19-v0.5.2.md); the charts
> in the README are generated from the measured data by `scripts/tcl-make-charts.py`.
>
> The v0.5.6 wallpaper A/B is separate: [2026-10-19-v0.5.6-wallpaper.md](2026-10-19-v0.5.6-wallpaper.md).
> The rows below remain v0.5.2 results and are not carried over to v0.5.6.

## Stock Home vs light launcher (`tcl-bench.sh`, 3 reps each)

| Run | Home | Idle CPU | MemAvailable | Jank % | p90 / p99 frame | Idle fps |
|---|---|---|---|---|---|---|
| baseline | stock `tvlauncher` | 53.0% | 448 MB | 87.6% | 46 / 105 ms | — |
| after 3 TCL packages disabled | stock | 59.3% | 384 MB | 86.3% | 42 / 73 ms | — |
| stock reference (2026-10-19 00:26, 3 reps) | stock | 57.3% | 427 MB | 85.9% | 48 / 150 ms | 60 |
| stock reference (2026-10-19 13:49, 5 reps) | stock | 20.6%¹ | 380 MB | 86.6% | 48 / 93 ms | 3.7 |
| launcher v0.1 (historical) | light | 15.5% | 528 MB | 7.7% | 13 / 24 ms | 0.1 |
| launcher v0.5.2 (2026-10-19 13:07, 5 reps) | Eddy | **14.5%** | **525 MB** | **2.2%** | 12 / 18 ms | **0.0** |

¹ The 13:49 stock row was sampled 45 s after the stock launcher was enabled; its feed animation was at
3.7 fps, so 20.6% is **not** the steady-state stock CPU. The 00:26 row (60 fps) is the steady-state
reference; the 13:49 row is kept for the same-session memory and app-start comparison.

Disabling the TCL packages gave **no measurable** gain (differences within run-to-run noise).

## Launcher versions (`tcl-launcher-bench.sh`)

Historical rows: 36 keys × 10 rounds. v0.5.2: three runs, 132 keys pooled (12:25 36, 13:26 36, 13:36 60).

| Version | Key response p50 / p95 / p99 / max | Full move p50 / max | Janky frames | Back to Home p50 / max | PSS |
|---|---|---|---|---|---|
| v0.2 | 13 / 16 / – / 19 ms | 641 / 749 ms | 9.2% | 56 / 206 ms | 29 MB |
| v0.3.3 normal | 14 / 19 / – / 20 ms | 641 / 696 ms | 16.2% | 39 / 236 ms | 20 MB |
| v0.3.3 ultralight | 16 / 29 / – / 34 ms | **17 / 41 ms** | **0%** | 36 / 191 ms | 20 MB |
| **v0.5.2 normal (132 keys)** | **13 / 16 / 17 / 19 ms** | 632 / 645 ms | **0.3%** (0.1–0.4%) | 38 / 181 ms | 18 MB (16–23) |
| v0.5.2 ultralight (20-key probe) | 16 / 32 / 32 / 32 ms | **16 / 45 ms** | **0.0%** of 23 | — | — |

Cold start: v0.3 (WaitTime, 5 runs) 470–551 ms, p50 514 ms · **v0.5.2** (15 runs across 3 batteries)
402–962 ms, p50 466 ms; 14 of 15 runs were ≤ 540 ms (one 962 ms outlier).

The v0.5.2 ultralight row is a short probe through the real on-TV menu, not a full battery; the
p50 movement (16 ms) matches the v0.3.3 ultralight behavior. It was restored to normal afterwards.
The v0.5.2 PSS row is the median of 23 / 18 / 18 MB; the 16 MB after the Home returns is the low end.

### v0.5.2 — first run, 2026-10-19 12:25 (pooled battery in the new file)

Package `br.eddy.tvhome`, versionName `0.5.2`, the build installed on the device.

| Metric | Value |
|---|---|
| Key response (key event → first reacting frame) | p50 **13 ms**, p95 16 ms, p99 17 ms, max 17 ms (36 keys) |
| Full move (key event → last frame of the movement) | p50 631 ms, max 645 ms |
| Janky frames | 0.4 % of 1367 |
| Cold start (Home) | p50 414 ms, max 483 ms (5 runs) |
| Back to Home | p50 39 ms, p95 181 ms, max 181 ms (10 runs) |
| Idle 60 s | total CPU 14.9 % of the 4-core capacity, **launcher 0 %**, 0.0 fps |
| PSS | 23 MB (21 MB after 10 Home returns) |

The launcher contributes **0 % CPU and 0 frames per second while the screen is idle**: it does not
poll, does not animate continuously, and has no background service. The 14.9 % total is the rest of
Android (see the idle-CPU findings in the device profile). The later runs measured 17.3 % and
15.6 %, and the 13:07 full battery 14.5 %; the spread is system noise around the same launcher.

Raw output of this run: `medicoes/launcher/20261019-122030-v0.5-atual/` (local only, git-ignored);
the other two runs are `20261019-132042-final-r1/` and `20261019-132900-final-v0.5.2/`, and the full
battery is in `medicoes/20261019-130714-final-0.5.2-full/`.

The environment these numbers were taken in is pinned in
[`data/2026-10-19-v0.5.2/environment.txt`](data/2026-10-19-v0.5.2/environment.txt): launcher
package and version, the SHA-256 of the installed APK and of the stored copy, the signer's
certificate fingerprint, the build fingerprint, the package counts, the memory figures and the
animation scales. It contains no serial number, no MAC address, no IP and no account, so the
measurement can be checked against the exact build it came from.

## v0.4 / v0.5 status

v0.4 (identity, on-TV settings, HDMI number keys, recovery action) and v0.5 (Globoplay button redirect
via window event) are **installed and running** on the device and pass the smoke test; v0.5.2 was
measured with the full battery and the three-run launcher battery on 2026-10-19
([detailed report](2026-10-19-v0.5.2.md)). The earlier v0.3.3 rows are kept so the version history
stays visible; they are not reused as v0.5 numbers.

## Reboot (`tcl-reboot-test.sh`)

Three `adb reboot` rounds on v0.5.2 (2026-10-19):

| Run | `adb reboot` → boot_completed | Home focused | Home launch (ActivityManager) | Smoke |
|---|---|---|---|---|
| r2 (14:21) | 39 s | 40 s | 1266 ms | pass |
| r3 (14:24) | 42 s | 42 s | 3603 ms | pass |
| r1 (14:19, discarded) | 39 s | 39 s | 1811 ms | pass (overlap) |

The Android part of the boot is unchanged from the lighter v0.3 (historical: 40 s to
`boot_completed`) and slightly faster than the historical stock Home row (43 s). The ActivityManager
Home launch time after boot varies (1.3–3.6 s). `adb reboot` cannot see the bootloader/kernel; the
power-loss timing with an external stopwatch is pending.

## Smoke test (24 checks) — 2026-10-19 12:20

24/24 OK, no crashes or ANRs. Two checks were flaky before a fix and are now stable:
`Home abre` (the focus needs more than 3 s to settle after the Home key) and the three voice checks
(the assistant needs ~10 s from `KEYCODE_SEARCH` to open the mic and reach the server, not 7 s).
`scripts/tcl-smoke.sh` now polls for those events instead of sleeping a fixed time.

## Not measured yet

- Power-loss boot with an external stopwatch (the bootloader/kernel phase is invisible to Android);
  the three `adb reboot` rounds are measured above.
- Standby resume cycles.
- Time-to-usable catalogue for Netflix/YouTube/Globoplay (the first frame is measured; the
  catalogue readiness is visual and needs a person).
- Accessibility-service key latency **on** v0.5.2 (the off path measured 100 events at p50 3 /
  p95 4 / p99 5 / max 6 ms; both on attempts collided and were discarded).
- Full ultralight battery (only a 20-key probe was run on v0.5.2).
- 8 h idle stability and weeks-long daily use of the debloat list.
