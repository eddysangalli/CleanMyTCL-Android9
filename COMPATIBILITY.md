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
| Debloat (profile list, 4 packages) | ✅ | One-by-one with `tcl-try-disable.sh`; smoke 24/24; 3 reboots OK |
| Launcher install + Home swap | ✅ | v0.3.3 → v0.5; Home resolves to the new launcher; stock launcher recoverable |
| Launcher performance | ✅ | Key response 1 frame (p50 14 ms, max 20 ms); real cold start ~0.5 s; PSS ~20 MB; 0 fps idle |
| Reboot (`adb reboot`) | ✅ | 3 runs, ~40–43 s to `boot_completed`, smoke OK after each |
| Power-loss boot (unplug/replug) | 🟡 | One real boot log analysed; external stopwatch measurement pending |
| Resume from standby (STR) | ⚪ | Not measured yet (this is the everyday "turn on") |
| HDMI 1/2/3 | 🟡 | Smoke test opens one HDMI input in `com.tcl.tv` and checks the session; the other inputs need a manual check |
| Live TV / tuner | ⚪ | Not tested after changes |
| Audio / picture / TCL settings | 🟡 | Speaker output and Settings open (smoke); TCL picture menus opened manually |
| Netflix / YouTube / Globoplay / Spotify | 🟡 | All open (first frame); time to usable catalogue not measured yet |
| IR remote | 🟡 | Daily use, no scripted test |
| Bluetooth remote (TCL_RC802VLA) | 🟡 | Paired check in smoke; key latency path measured (p50 2 ms) |
| Microphone / Assistant | 🟡 | Assistant opens, HAL mic opens, server connects (smoke). Remote-mic latency not measured |
| Chromecast built-in | ⚪ | Process running; casting not tested after changes |
| Google Home control | ⚪ | Not tested |
| Wi-Fi / Ethernet | 🟡 | Internet check in smoke (Ethernet); Wi-Fi not re-tested |
| Google Play / DRM playback | 🟡 | Play Store present; Netflix opens; protected playback not explicitly verified after changes |
| Remap of the "Globoplay" button | 🧪 | No root: the key is injected by TCL software; v0.5 redirects by window event (accessibility service) |

### Other TCL Android 9 models

| Model | Status | Notes |
|---|---|---|
| TCL BeyondTV (India set, PPR1.180610.011) | 🔵 | Upstream CleanYourTV profile [`examples/tcl-beyondtv`](examples/tcl-beyondtv/debloat.txt) (not reproduced here) |
| Any other TCL Android 9 | ⚪ | Please contribute a profile: [devices/README.md](devices/README.md) |
| TCL Google TV / Android 11+ | ⚪ | Out of scope of this fork; see upstream CleanYourTV |
