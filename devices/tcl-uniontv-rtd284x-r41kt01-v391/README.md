# TCL L32S6500 (UnionTV) — Realtek RTD284x — R41KT01 V391

| Field | Value |
|---|---|
| `ro.product.model` / brand | UnionTV / TCL |
| Fingerprint | `TCL/UnionTV/UnionTV:9/PPR2.180905.006.A1/builder.20210125.083312:user/release-keys` |
| Firmware | `V8-R41KT01-LF1V391` (client `TCL-CA-RT41KA-S1`, Central America) |
| Android | 9 (API 28), 32-bit userspace (`armeabi-v7a`) |
| RAM | ~1.4 GB visible to Android (`MemTotal` 1,437,388 kB) |
| Storage | eMMC, `/data` 4.2 GB |
| Display | UI at 1280×720, density 213 |
| Remote | IR only (no Bluetooth remote paired, no microphone) |
| Commercial name | TCL **L32S6500** (32-inch S6500 line), from the label on the back; the system only reports `UnionTV` |

How to know if your TV matches: `adb shell getprop ro.build.fingerprint` and
`adb shell getprop ro.software.version_id`. Same fingerprint = closest match. Different firmware = treat as
**not tested** and follow [../README.md](../README.md) to create your own profile.

## Files

- [`debloat.txt`](debloat.txt) — four packages (telemetry and TCL promo), each disabled alone with the
  smoke test, then a reboot test. One session only: see the notes at the top of the file.

## Smoke test on this TV

The BeyondTV2 defaults do not fit this unit (no Bluetooth remote, no microphone in the audio HAL log,
Netflix not used), so run it with:

```bash
TCL_REMOTE_NAME= TCL_MIC_HAL_LOG= \
TCL_SMOKE_APP=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity \
bash scripts/tcl-smoke.sh
```

All other checks pass as they are: processes, internet, Bluetooth on, speaker, Home, Settings, HDMI input
session (`HW1413744128`), YouTube, Assistant listening and connecting.

## What was checked (2026-10-20)

- **ADB:** over the network on port 5555 ("Network debugging" in Developer options). USB does not work:
  the TV's USB ports are host-only. A VPN on the computer that routes the TV's subnet makes
  `adb connect` time out; disconnect it while working with the TV.
- **Launcher:** `br.eddy.tvhome` installed, set as Home with the stock `com.google.android.tvlauncher`
  disabled (`pm disable-user`, recoverable), survived reboots.
- **Debloat:** the four packages in `debloat.txt`, one at a time with `tcl-try-disable.sh`, all passed;
  `tcl-reboot-test.sh` afterwards: 63 s from `adb reboot` to `boot_completed`, disabled packages kept,
  Home unchanged, smoke test OK.
- **Launcher benchmark** (`tcl-launcher-bench.sh`, 36 keys, with the M3E spring from `perf/glass-spring`):
  key response p50 20 ms / p95 21 ms, full move p50 413 ms, 0.0% janky frames, idle 0 fps and 0% CPU for
  the launcher, PSS 22–28 MB across runs.

Not tested: Live TV reception (no antenna), Chromecast casting, standby resume, several days of use.
