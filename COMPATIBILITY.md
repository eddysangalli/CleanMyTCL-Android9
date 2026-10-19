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
| Launcher install + Home swap | ✅ | v0.3.3 → v0.5.2 → **v0.5.4** (installed on 2026-10-19, versionCode 15); Home resolves to the new launcher; stock launcher recoverable |
| Launcher performance | ✅ | Measured on **v0.5.2** (2026-10-19): 3 runs / 132 keys gave key response p50 13 ms / p95 16 / p99 17 / max 19 ms, full move p50 632 ms (ultralight probe: 16 ms), cold start p50 466 ms (14/15 ≤ 540 ms, one 962 ms outlier), back-to-Home p50 38 ms, jank 0.3 % (launcher runs); the 5-rep full battery gave jank 2.2 %, idle CPU 14.5 % with the launcher at 0 % and 0 fps, PSS 18 MB (16–23). Details in [benchmarks/results/2026-10-19-v0.5.2.md](benchmarks/results/2026-10-19-v0.5.2.md). **The source and the TV are now v0.5.4 (versionCode 15); the v0.5.4 battery is pending — the numbers above stay as the measured baseline** |
| Reboot (`adb reboot`) | ✅ | 3 runs, ~40–43 s to `boot_completed`, smoke OK after each |
| Smoke test (24 checks) | ✅ | Re-run 2026-10-19 12:20 — 24/24 OK, no crashes/ANR. See the timing fix in `scripts/tcl-smoke.sh` |
| Power-loss boot (unplug/replug) | 🟡 | One real boot log analysed; external stopwatch measurement pending |
| Resume from standby (STR) | ⚪ | Not measured yet (this is the everyday "turn on") |
| HDMI 1/2/3 | 🟡 | **Number keys 1-3 verified**: each opens `com.tcl.tv` on the matching input (2026-10-19). The system exposes three HDMI inputs; the hardware block also reports a fourth HDMI port, but it has no input entry, so key 4 has nothing to open here. Two inputs report `state: 0` (connected) and two `state: 1`; playing content on them is still a visual check |
| Live TV / tuner | 🟡 | `com.tcl.tv` opens and takes focus (2026-10-19); tuner input listed by `dumpsys tv_input`. No antenna/cable connected, so reception is untested |
| Audio / picture / TCL settings | 🟡 | Speaker output and Settings open (smoke); TCL picture menus opened manually |
| Netflix / YouTube / Globoplay / Spotify | 🟡 | All open. Cold first frame (WaitTime, 3 reps each): Netflix 888 ms · YouTube 1100 ms · Globoplay 2859 ms · Spotify 1212 ms. Eddy is faster than the stock Home on 4 of 5 apps in the same-session comparison. **Time to a usable catalogue is a visual check and is still pending**: Netflix draws its catalogue in a DRM-protected custom surface, so neither `screencap` nor the accessibility tree can see it over ADB |
| IR remote | ✅ | Real D-pad events written to the IR receiver (`/dev/input/event0`, `mtkinp`) with `sendevent`: key delivery p50 2 ms, p95 4, p99 4, max 4 ms over 60 events. This is the physical path, including the input reader and the dispatcher; the 13 ms figure is for ADB-injected keys, which skip the reader |
| Bluetooth remote (TCL_RC802VLA) | 🟡 | Paired (bonds listed in `dumpsys bluetooth_manager`); key latency path measured (p50 2 ms) with the same method as the IR one |
| Microphone / Assistant | 🟡 | Assistant opens, HAL mic opens, server connects (smoke). Search → UI 45 ms, mic 60 ms, server 886 ms. Whether the microphone actually captures voice with the remote needs a person |
| Chromecast built-in | ⚪ | Process running (`com.google.android.apps.mediashell`); casting not tested after changes |
| Google Home control | ⚪ | Not tested |
| Wi-Fi / Ethernet | 🟡 | Internet check in smoke (Ethernet); Wi-Fi not re-tested |
| Google Play / DRM playback | 🟡 | Play Store present; Netflix opens; `android.hardware.drm@1.1-service.widevine`, clearkey, `drmserver` and `mediadrmserver` all running. Protected playback itself is a visual/audio check |
| Remap of the "Globoplay" button | 🧪 | No root: the key is injected by TCL software. v0.5 redirects by window event, which needs the accessibility service — **currently off**, and its cost to key delivery is **not measured** (the two attempts that were run collided with other measurements) |

### Other TCL Android 9 models

| Model | Status | Notes |
|---|---|---|
| TCL BeyondTV (India set, PPR1.180610.011) | 🔵 | Upstream CleanYourTV profile [`examples/tcl-beyondtv`](https://github.com/Gathin23/cleanyourtv/blob/main/examples/tcl-beyondtv/debloat.txt) (not reproduced here) |
| Any other TCL Android 9 | ⚪ | Please contribute a profile: [devices/README.md](devices/README.md) |
| TCL Google TV / Android 11+ | ⚪ | Out of scope of this fork; see upstream CleanYourTV |
