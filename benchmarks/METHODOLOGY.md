# Benchmark methodology

**Performance is the top rule.** A feature that adds a perceptible delay (≈ 1 s is already unacceptable)
is reworked or reverted. Report p50 **and** tail (p95, p99, max) — a better average with occasional
stalls is a regression.

## Conditions

- Same TV, same firmware, same network (cable preferred), TV idle ≥ 2 min before a run.
- **No scrcpy, no screen recording** during runs. No other ADB activity.
- ≥ 3 repetitions (keys: ≥ 36 presses × 10 rounds). Record date, build, firmware.
- Compare versions back-to-back in the same session when possible.

## Scripts

| Metric | Script | Notes |
|---|---|---|
| Idle CPU, MemAvailable, processes, app first frame, jank | `tcl-bench.sh <label> [reps]` | Appends to `medicoes/historico.csv` |
| Key response and full move time (p50/p95/p99/max) | `tcl-launcher-bench.sh <label>` | Uses `dumpsys gfxinfo framestats` |
| Real key path latency (with/without accessibility) | `tcl-keypath-bench.sh <label>` | `settings put global eddy_debug_keys 1` |
| Launcher cold start (WaitTime, not TotalTime) | `tcl-coldstart.sh <label>` | TotalTime on Android 9 over-reports (~3.7 s vs real ~0.5 s) |
| Idle CPU / fps of the visible screen | `tcl-idle-probe.sh <label> [s]` | |
| Reboot to `boot_completed` / Home drawn | `tcl-reboot-test.sh`, `tcl-boot-test.sh <label> [app] [s]` | Kernel/bootloader time needs an external stopwatch |
| App start (first screen, screenshots over time) | `tcl-app-start-test.sh <app> <label>` | "Usable catalogue" is judged from screenshots |
| Hotword cost | `tcl-hotword-test.sh <label>` | |

## Still to define

- Standby (STR) resume: external stopwatch from power key to usable Home, 10 reps.
- Time to usable catalogue (Netflix, YouTube, Globoplay): screenshot series, cold vs warm, 3 states
  (right after boot, 10 min after boot, stable).
- Long-run stability: idle probe every 30 min for 8 h.

Raw outputs stay local (`medicoes/`, git-ignored). Only curated tables go to `benchmarks/results/`.
