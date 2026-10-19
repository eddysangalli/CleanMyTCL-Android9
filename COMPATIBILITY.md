# Compatibility matrix

Each feature is validated **separately**. A green "launcher" does not mean HDMI or reboot are validated.

## Status legend

| Status | Meaning |
|---|---|
| ✅ Tested and validated | Tested by a maintainer on this exact firmware, with the scripted test and its log |
| 🟡 Partially tested | Some checks passed; others not yet run (listed in the profile) |
| 🔵 Reported by contributors | Reported working by someone else, not reproduced by maintainers |
| 🧪 Experimental | Works in limited tests, may change or be reverted |
| ⚪ Not tested | No data |
| ❌ Incompatible | Known not to work / not possible without root |

## Devices

### TCL BeyondTV2 — Realtek RTD2851 — R851T02 V660 — Android 9 (API 28), 32-bit, 2 GB RAM

Profile: [devices/tcl-beyondtv2-rtd2851-r851t02-v660/](devices/tcl-beyondtv2-rtd2851-r851t02-v660/) · Last checked: 2026-10-19

| Area | Status | Evidence / notes |
|---|---|---|
| ADB over network | ✅ | Port 5555, Windows 11 + platform-tools |
| Backup / restore of package state | ✅ | `tcl-backup.ps1` / `tcl-restore.ps1` (dry-run and apply used on 2026-10-18) |
| Debloat (profile list, 2 packages) | ✅ | `debloat.txt` lists 2 packages (`com.tcl.bi`, `com.tcl.tv.tclhome_passive`); one-by-one with `tcl-try-disable.sh`, smoke test, reboots OK. Not benchmarked over several days. `com.tcl.esticker` is **not** listed — it did not stay disabled |
| Launcher install + Home swap | ✅ | v0.3.3 → v0.5.2; Home resolves to the new launcher; stock launcher recoverable |
| Launcher performance | ✅ | Measured on **v0.5.2** (2026-10-19): 3 runs / 132 keys gave key response p50 13 ms / p95 16 / p99 17 / max 19 ms, full move p50 632 ms (ultralight probe: 16 ms), cold start p50 466 ms (14/15 ≤ 540 ms, one 962 ms outlier), back-to-Home p50 38 ms, jank 0.3 % (launcher runs); the 5-rep full battery gave jank 2.2 %, idle CPU 14.5 % with the launcher at 0 % and 0 fps, PSS 18 MB (16–23). Details in [benchmarks/results/2026-10-19-v0.5.2.md](benchmarks/results/2026-10-19-v0.5.2.md) |
| Reboot (`adb reboot`) | ✅ | 3 runs, ~40–43 s to `boot_completed`, smoke OK after each |
| Smoke test (24 checks) | ✅ | Re-run 2026-10-19 12:20 — 24/24 OK, no crashes/ANR. See the timing fix in `scripts/tcl-smoke.sh` |
| Power-loss boot (unplug/replug) | 🟡 | One real boot log analysed; external stopwatch measurement pending |
| Resume from standby (STR) | ⚪ | Not measured yet (this is the everyday "turn on") |
| HDMI 1/2/3 | 🟡 | Smoke test opens one HDMI input in `com.tcl.tv` and checks the session; the other inputs need a manual check |
| Live TV / tuner | ⚪ | Not tested after changes |
| Audio / picture / TCL settings | 🟡 | Speaker output and Settings open (smoke); TCL picture menus opened manually |
| Netflix / YouTube / Globoplay / Spotify | 🟡 | All open (first frame measured with WaitTime; Eddy is faster on 4 of 5 apps in the same-session stock comparison); time to usable catalogue is a visual check and is pending |
| IR remote | 🟡 | Daily use, no scripted test |
| Bluetooth remote (TCL_RC802VLA) | 🟡 | Paired check in smoke; key latency path measured (p50 2 ms) |
| Microphone / Assistant | 🟡 | Assistant opens, HAL mic opens, server connects (smoke). Whether the microphone actually captures voice with the remote needs a person |
| Chromecast built-in | ⚪ | Process running; casting not tested after changes |
| Google Home control | ⚪ | Not tested |
| Wi-Fi / Ethernet | 🟡 | Internet check in smoke (Ethernet); Wi-Fi not re-tested |
| Google Play / DRM playback | 🟡 | Play Store present; Netflix opens; protected playback not explicitly verified after changes |
| Remap of the "Globoplay" button | 🧪 | No root: the key is injected by TCL software; v0.5 redirects by window event (accessibility service) |

### Other TCL Android 9 models

| Model | Status | Notes |
|---|---|---|
| TCL BeyondTV (India set, PPR1.180610.011) | 🔵 | Upstream CleanYourTV profile [`examples/tcl-beyondtv`](https://github.com/Gathin23/cleanyourtv/blob/main/examples/tcl-beyondtv/debloat.txt) (not reproduced here) |
| Any other TCL Android 9 | ⚪ | Please contribute a profile: [devices/README.md](devices/README.md) |
| TCL Google TV / Android 11+ | ⚪ | Out of scope of this fork; see upstream CleanYourTV |
