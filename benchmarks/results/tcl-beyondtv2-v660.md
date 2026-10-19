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

| Version | Key response p50 / p95 / max | Full move p50 / max | Janky frames | Back to Home p50 / max | PSS |
|---|---|---|---|---|---|
| v0.2 | 13 / 16 / 19 ms | 641 / 749 ms | 9.2% | 56 / 206 ms | 29 MB |
| v0.3.3 normal | 14 / 19 / 20 ms | 641 / 696 ms | 16.2% | 39 / 236 ms | 20 MB |
| v0.3.3 ultralight | 16 / 29 / 34 ms | **17 / 41 ms** | **0%** | 36 / 191 ms | 20 MB |

Cold start v0.3 (WaitTime, 5 runs): 470–551 ms, p50 514 ms.

## v0.4 / v0.5 — installed, smoke-tested, NOT re-benchmarked

v0.4 (identity, on-TV settings, HDMI number keys, recovery action) and v0.5 (Globoplay button redirect via
window event) are **installed and running** on the device and pass the smoke test and reboot test, but the
full key-latency / PSS / jank battery has **not** been re-run against them.

**Do not read the v0.3.3 numbers above as v0.4/v0.5 numbers.** The v0.3.3 figures are the last measured
state of the launcher. Re-running the benchmark is an open task; until it is done, the honest statement is:
*v0.3.3 was measured; v0.5 is in daily use but unmeasured.*

Changes since v0.3.3 are behavioural/UI (settings screen, HDMI shortcuts, identity strings, one accessibility
service for the Globoplay key) and do not add background services, polling, network calls or continuous
animation — but that is a code-review argument, not a measurement.

## Reboot (`tcl-reboot-test.sh`)

| Run | `adb reboot` → boot_completed | Home focused | Home drawn |
|---|---|---|---|
| stock Home | 43 s | 44 s | 3.8 s |
| light Home | 40 s | 40 s | 3.1 s |

## Not measured yet

Standby resume, power-loss boot with stopwatch, Netflix/YouTube/Globoplay time-to-usable,
v0.4–v0.5 full bench, accessibility-service key latency, 8 h stability.
