# Benchmark methodology

**Performance is the top rule.** A feature that adds a perceptible delay (≈ 1 s is already unacceptable)
is reworked or reverted. Report p50 **and** tail (p95, p99, max) — a better average with occasional
stalls is a regression.

## Conditions

- Same TV, same firmware, same network (cable preferred), TV idle ≥ 2 min before a run.
- **No scrcpy, no screen recording** during runs. No other ADB session and no other test touching
  the TV at the same time: an overlapping run contaminates both samples (even a key press or a
  screenshot changes the CPU/fps figures).
- Main battery: **≥ 5 repetitions**. Launcher battery: ≥ 36 keys per run, ≥ 3 runs, pooled by raw
  sample. Record date, build, firmware.
- Compare versions back-to-back in the same session when possible; if the launcher's visual state
  differs between the two sides (e.g., an animated stock feed vs a static one), label the states
  instead of comparing the averages directly.
- **CPU units:** the system-wide figure (idle CPU) is the share of the **whole 4-core SoC**; a
  per-process row is the share of **one core**. Never mix the two.
- **App first frame is `am start -W` WaitTime.** `TotalTime` on Android 9 can include a previous
  activity transition (~3.7 s in the Settings→Home sequence) and must not be used for that table.
- **What the key numbers mean.** The launcher battery injects keys with `adb shell input keyevent`.
  The response time (13 ms median in v0.5.2) is from that injected event to the first frame that
  reacts; it is the launcher's reaction time and **excludes the remote and the IR/BT receiver**.
  The real input path (IR receiver `/dev/input/eventN` → launcher) was measured separately with
  `tcl-keypath-bench.sh` (p50 3 ms, 100 events). The remote's own scan/transmit time was not
  measured; the end-to-end physical-remote latency would need a hardware probe or a high-speed
  camera. Do not present the ADB figure as physical-remote latency.

## Scripts

| Metric | Script | Notes |
|---|---|---|
| Idle CPU, MemAvailable, processes, app first frame, jank | `tcl-bench.sh <label> [reps]` | Appends to `medicoes/historico.csv`; the summary and the `*_wait_ms` columns use WaitTime |
| Key response and full move time (p50/p95/p99/max) | `tcl-launcher-bench.sh <label> [keys] [rounds]` | Uses `dumpsys gfxinfo framestats` |
| Real key path latency (with/without accessibility) | `tcl-keypath-bench.sh <label> [keys]` | `settings put global eddy_debug_keys 1` |
| Launcher cold start (WaitTime, not TotalTime) | `tcl-coldstart.sh <label>` | TotalTime on Android 9 over-reports (~3.7 s vs real ~0.5 s) |
| Idle CPU / fps of the visible screen | `tcl-idle-probe.sh <label> [s]`, `tcl-idle-clean.sh <label> [s]` | `idle-clean` force-stops the test apps first |
| Stock Home in the same session | `tcl-bench-stock.sh <label> [reps]`, `tcl-stock-session.sh <cmd>` | Enables the stock launcher and restores Eddy in the EXIT trap |
| Reboot to `boot_completed` / Home drawn | `tcl-reboot-test.sh`, `tcl-boot-test.sh <label> [app] [s]` | Kernel/bootloader time needs an external stopwatch |
| App start (first screen, screenshots over time) | `tcl-app-start-test.sh <app> <label>` | "Usable catalogue" is judged from screenshots |
| Views, AppContexts, PSS and the Skia RenderTarget cache | `tcl-view-count.sh <label> [moves]` | `am force-stop`, opens Home, reads before and after navigating; the GPU figure only exists in `gfxinfo` on Android 9 (`meminfo` reports `Graphics: 0`) |
| Hotword cost | `tcl-hotword-test.sh <label>` | |
| Screenshots | `tcl-screencap.sh <file.png> [host]` | Binary PNG with a signature check; never use PowerShell 5.1 `>` |
| Charts | `tcl-make-charts.py <data.json> <out-dir>` | Pillow; the JSON is the single source of the numbers |

## A trap: `am force-stop` takes the accessibility services with it

On this TCL, `am force-stop <launcher>` **clears** `enabled_accessibility_services`, and the
service does not come back when the Home opens again. Upstream PR #503 reported it on a 55C715;
it was reproduced on the BeyondTV2 V660 on 2026-10-20 — the setting went from
`br.eddy.tvhome/com.gathin.tvhome.KeyRemapService` to empty after one stop, and stayed empty
after the Home was reopened.

Every measurement that force-stops the launcher therefore has to put the list back:
`tv_acc_save` before the first stop and `tv_acc_restore` at the end (both in `scripts/tcl-lib.sh`,
wired with a `trap … EXIT` in `tcl-coldstart.sh`, `tcl-launcher-bench.sh` and
`tcl-view-count.sh`). The restore **appends**: the list is shared by every app, so writing one
service on its own would wipe the others.

In `tools/bench.py` the restore also runs **after** the measurement, not before: writing that
setting makes the system bind the service, which starts the launcher process, and a "cold start"
measured on a running process is a warm one.

## Pending measurements (as of 2026-10-20)

- Power-loss boot with an external stopwatch (bootloader/kernel); `adb reboot` only covers Android.
- Standby (STR) resume: power key to usable Home, 10 reps.
- Time to usable catalogue (Netflix, YouTube, Globoplay): screenshot series, cold vs warm.
- Accessibility-service key latency **on** v0.5.8 (the off path is measured: 100 events, p50 3 /
  p95 4 / p99 5 / max 6 ms). Now that the bench scripts no longer drop the service, this can be
  measured without the run destroying it first.
- Full ultralight battery (only a 20-key probe so far).
- The complete v0.5.8 battery: five matched repetitions per preset. Only the single
  `tools/bench.py` run and the `tcl-launcher-bench.sh` A/B of 2026-10-20 exist.
- The physical remote on the "Input/Source" button, after the `InputKeyService` change dropped its
  window-event subscription (upstream #150). Key injection does not reach an accessibility filter
  on this firmware, so this needs a person with the remote.
- Long-run stability: idle probe every 30 min for 8 h; weeks of daily use of the debloat list.

## Reporting rules

- Every number carries the launcher version, the date and the sample size. Old rows keep their
  version tag and are never reused for a newer version.
- Report the median (p50) and the tail (p95, p99, max); a better p50 with a worse tail is a
  regression. Say explicitly when a difference is inside the run-to-run dispersion.
- Raw outputs stay local (`medicoes/`, git-ignored). Only curated tables go to `benchmarks/results/`.
- Charts are generated by `scripts/tcl-make-charts.py` from a JSON file that cites the results
  page; no number is typed into a drawing tool.
