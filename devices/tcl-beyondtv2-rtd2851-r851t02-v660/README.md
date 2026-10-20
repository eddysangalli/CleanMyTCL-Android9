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

**Installed for validation on 2026-10-20 11:38:** `br.eddy.tvhome` **0.5.7** (versionCode 29), APK
SHA-256 `AAE067C60E8D1E0D90162BAA9DFB1DC87C2CFDDDC2ACB7CACB764D5A6FC7A712`, built from
`integrate/all-prs` (integrated PRs #6, #7, #8, #10 and #11 from @roky146, plus the audited
upstream ports). The `base.apk` pulled back from the TV hashes to the same value. This is a
test/validation build, **not a release**; `main` and the published reference remain v0.5.6 (APK
`8D5C15983C31F356B46D23EAFE7626C6B59E03084FE678BD9ECEE84162EFCE1C`, launcher commit `28a0383`).
The previous 0.5.7 validation build (versionCode 28, SHA-256 `ACDF90EC…`) is kept as
`medicoes/apks/tvhome-0.5.7-code28.apk` for comparison. The earlier local 0.5.6.1 Globoplay hotfix
(versionCode 18) is superseded by this build.

> Reinstalling the APK disables the accessibility service. After each install, re-enable it before
> testing the Globoplay key:
> `adb shell settings put secure enabled_accessibility_services br.eddy.tvhome/com.gathin.tvhome.KeyRemapService`
> and `adb shell settings put secure accessibility_enabled 1`. The validation install had the service
> re-enabled and bound (confirmed after the code 29 install).

The full v0.5.6 and v0.5.7 batteries are pending; the measured performance baseline remains v0.5.2.
The wallpaper A/B is separate:
[key p50 17 ms, spring p50 ~634 ms, jank 0.19% black vs 6.89% aurora, PSS 23 vs 32 MB](../../benchmarks/results/2026-10-19-v0.5.6-wallpaper.md).

The validation build adds Performance/Balanced/Visual presets, the 0.5.6 columns/profiles/static
Aurora wallpaper, the local Globoplay redirect settling fix, and the reviewed contributions from
@roky146: configurable smoke scripts (#2/#3), localized weather (#4), Spanish strings (#5) and the
focus-ring fix (#9), followed by the local cold-start ring refit. The code 29 build adds the
integrated #6 (options panel with steppers), #7 (extra backgrounds), #8 (BlurHash), #10
(Material 3 Expressive spring) and #11 (TCL L32S6500 profile) plus the audited upstream ports
(font weights built once, integer colour sampling, Home guard in the debloat scripts). Existing
preferences migrate to **Custom** without changing motion or wallpaper. The full v0.5.7 battery,
including the preset state, is still pending. Review status:
[`docs/PR-REVIEW-ROKY146-2026-10-20.md`](../../docs/PR-REVIEW-ROKY146-2026-10-20.md).

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
