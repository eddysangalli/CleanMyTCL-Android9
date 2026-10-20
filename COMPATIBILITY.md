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
| Launcher install + Home swap | ✅ | v0.3.3 → v0.5.2 → v0.5.4 → v0.5.5 → **v0.5.6** (installed on 2026-10-19 22:14, versionCode 17); Home resolves to the new launcher; stock launcher recoverable |
| Launcher columns, profiles and wallpaper | 🟡 | v0.5.6: 4/5/6 columns on the TV; family profiles store name/message/accent/wallpaper (no image data or service); static 1920×1080 aurora decoded RGB_565 off the main thread, one bitmap released on switch/destroy; ultralight always black. Functional checks passed. Wallpaper A/B: key p50 17 ms and spring p50 ~634 ms in both, jank 0.19% black vs 6.89% aurora (108 keys each), PSS 23 vs 32 MB. Full v0.5.6 battery/soak pending; see [2026-10-19-v0.5.6-wallpaper.md](benchmarks/results/2026-10-19-v0.5.6-wallpaper.md) |
| Launcher performance | ✅ | Measured on **v0.5.2** (2026-10-19): 3 runs / 132 keys gave key response p50 13 ms / p95 16 / p99 17 / max 19 ms, full move p50 632 ms (ultralight probe: 16 ms), cold start p50 466 ms (14/15 ≤ 540 ms, one 962 ms outlier), back-to-Home p50 38 ms, jank 0.3 % (launcher runs); the 5-rep full battery gave jank 2.2 %, idle CPU 14.5 % with the launcher at 0 % and 0 fps, PSS 18 MB (16–23). Details in [benchmarks/results/2026-10-19-v0.5.2.md](benchmarks/results/2026-10-19-v0.5.2.md). **The source and the TV are now v0.5.6 (versionCode 17); the full v0.5.6 battery is pending — the numbers above stay as the measured baseline. The wallpaper-specific A/B is in [2026-10-19-v0.5.6-wallpaper.md](benchmarks/results/2026-10-19-v0.5.6-wallpaper.md)** |
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

### TCL UnionTV — Realtek RTD284x — R41KT01 V391 — Android 9 (API 28), 32-bit, ~1.4 GB RAM

Profile: [devices/tcl-uniontv-rtd284x-r41kt01-v391/](devices/tcl-uniontv-rtd284x-r41kt01-v391/) · Last checked: 2026-10-20 · Reported by a contributor, not reproduced by maintainers

| Area | Status | Evidence / notes |
|---|---|---|
| ADB over network | 🔵 | Port 5555, Windows 11 + platform-tools. USB not possible (host-only ports) |
| Backup of package state | 🔵 | `tcl-backup.ps1` before any change |
| Debloat (profile list, 4 packages) | 🔵 | `com.tcl.bi`, `com.tcl.tv.tclhome_passive`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender`: one at a time with `tcl-try-disable.sh`, smoke OK after each. One session, not several days; no speed benchmark |
| Launcher install + Home swap | 🔵 | `br.eddy.tvhome` 0.5.6 as Home, stock launcher disabled and recoverable |
| Launcher performance | 🔵 | `tcl-launcher-bench.sh`, 36 keys: response p50 20 / p95 21 ms, full move p50 413 ms (M3E spring), 0.0% jank, idle 0 fps |
| Reboot (`adb reboot`) | 🔵 | 1 run after the debloat: 63 s to `boot_completed`, disabled packages kept, smoke OK |
| Smoke test | 🔵 | Passes with `TCL_REMOTE_NAME=`, `TCL_MIC_HAL_LOG=` and YouTube as `TCL_SMOKE_APP` (see the profile) |
| HDMI | 🔵 | Input `HW1413744128` opens in `com.tcl.tv` with an active session (smoke) |
| IR remote | 🔵 | D-pad, Home, Back and Menu used throughout; latency not measured with `sendevent` |
| Bluetooth remote / microphone | ⚪ | This unit has neither |
| Assistant | 🔵 | Opens, listens and connects (smoke); voice capture not checked |
| Live TV / tuner, Chromecast, standby | ⚪ | Not tested |

### Other TCL Android 9 models

| Model | Status | Notes |
|---|---|---|
| TCL BeyondTV (India set, PPR1.180610.011) | 🔵 | Upstream CleanYourTV profile [`examples/tcl-beyondtv`](https://github.com/Gathin23/cleanyourtv/blob/main/examples/tcl-beyondtv/debloat.txt) (not reproduced here) |
| Any other TCL Android 9 | ⚪ | Please contribute a profile: [devices/README.md](devices/README.md) |
| TCL Google TV / Android 11+ | ⚪ | Out of scope of this fork; see upstream CleanYourTV |
