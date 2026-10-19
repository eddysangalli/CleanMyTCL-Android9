# TCL BeyondTV2 — Realtek RTD2851 — R851T02 V660

| Field | Value |
|---|---|
| `ro.product.model` / brand | BeyondTV2 / TCL |
| Fingerprint | `TCL/BeyondTV2/BeyondTV:9/PPR1.180610.011/AP08:user/release-keys` |
| Firmware | `V8-R851T02-LF1V660` (client `TCL-LA-RT2851-S1`, Latin America) |
| Android | 9 (API 28), 32-bit userspace |
| RAM | 2 GB physical, zRAM ~800 MB |
| Storage | eMMC, `/data` 10 GB |
| Remote | TCL_RC802VLA (Bluetooth, mic) + IR |
| Commercial name | Not exposed by the system (probably TCL P715/C715 2020 line, Brazil) |

How to know if your TV matches: `adb shell getprop ro.build.fingerprint` and
`adb shell getprop ro.product.model`. Same fingerprint = closest match. Different firmware = treat as
**not tested** and follow [../README.md](../README.md) to create your own profile.

## Files

- [`debloat.txt`](debloat.txt) — validated list (upstream `tools/disable.sh` format).
- [`../../scripts/protected-packages.txt`](../../scripts/protected-packages.txt) — packages the scripts refuse to disable.
- [`OPTIMIZATION-CANDIDATES.md`](OPTIMIZATION-CANDIDATES.md) — measured candidates, **not applied**, each with risk and undo.

**Currently installed:** `br.eddy.tvhome` **0.5.5** (versionCode 16, installed 2026-10-19 17:16),
APK SHA-256 `17C70DFBE36F00DEB033E841FBB75AC4C73BE868382F24CEA3EC1BA54E10AE9F` — identical to the
stored `tvhome-0.5.5.apk` (launcher commit `c250429`). The performance battery for 0.5.5 is pending;
the measured figures below are from v0.1–v0.5.2.

## Key findings on this hardware (measured)

- The largest idle CPU cost is the hands-free **"Ok Google" listening**: `voice_ap` (TCL, built-in mics)
  + `katniss:interactor` (Assistant) ≈ 48% of one core, continuously. Re-confirmed 2026-10-19 (≈ 27% + 25%).
- Replacing the stock Home with the light launcher cut idle CPU from ~53–57% (stock Home animating
  continuously at 60 fps) to ~15% **of the whole 4-core SoC**, and idle fps from 60 to 0.
  *Measured on launcher v0.1–v0.5.2; v0.5.2: 14.5% in the 5-rep full battery and 14.9/15.6/17.3%
  across three launcher runs; the launcher itself is 0% of one core and 0 fps; PSS 18 MB (16–23).
  A cold stock sample on 2026-10-19 at 13:49 read 20.6% at 3.7 fps before its feed animation ramped
  up, so the steady-state stock figure remains the 57.3% at 60 fps. See
  [../../benchmarks/results/tcl-beyondtv2-v660.md](../../benchmarks/results/tcl-beyondtv2-v660.md)
  and the [detailed v0.5.2 battery](../../benchmarks/results/2026-10-19-v0.5.2.md).*
- Disabling TCL telemetry/promo packages removed processes but gave **no measurable** speed gain.
- Memory is not the bottleneck: 0 apps killed by low memory in boot logs; zRAM/swap tweaks are out of scope.
- Netflix first frame ~0.86–0.94 s (`am start -W` WaitTime, 5 runs). The “~90 s” figure was the wall
  time of an `am start` + `screencap` sequence; `screencap` hangs on the DRM surface, so it is not
  the app's load. The time until the catalogue is usable is measured with a person:
  `scripts/tcl-app-assisted.sh netflix <label> frio`.
- The remote's "Globoplay" key is injected by TCL software (scanCode 0); it cannot be remapped by a
  key filter without root.
