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

## Key findings on this hardware (measured)

- The largest idle CPU cost is the hands-free **"Ok Google" listening**: `voice_ap` (TCL, built-in mics)
  + `katniss:interactor` (Assistant) ≈ 48% of one core, continuously. Re-confirmed 2026-10-19 (≈ 27% + 25%).
- Replacing the stock Home with the light launcher cut idle CPU from ~53–57% to ~15% (of one core,
  system-wide) and idle fps from 60 to 0 (stock home animates permanently).
  *Measured on launcher v0.1–v0.3.3; see [../../benchmarks/results/tcl-beyondtv2-v660.md](../../benchmarks/results/tcl-beyondtv2-v660.md).*
- Disabling TCL telemetry/promo packages removed processes but gave **no measurable** speed gain.
- Memory is not the bottleneck: 0 apps killed by low memory in boot logs; zRAM/swap tweaks are out of scope.
- Netflix first frame ~3.8 s; the long wait (~90 s reported) is after that and is still under investigation.
- The remote's "Globoplay" key is injected by TCL software (scanCode 0); it cannot be remapped by a
  key filter without root.
