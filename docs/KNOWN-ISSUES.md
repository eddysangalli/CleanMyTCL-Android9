# Known issues and open investigations

## Known issues (TCL BeyondTV2 / V660)

- `com.google.android.tvrecommendations` crashes once per boot after the stock launcher is disabled
  (looks for `LAUNCH_BOOT_HELPER`). Harmless; candidate #2 in the profile.
- The remote's **Globoplay** key is injected by TCL software (scanCode 0, no `/dev/input` event):
  accessibility key filters do not see it. Launcher v0.5 redirects by window event instead (experimental).
- `adb shell input --longpress` does not really hold on Android 9: "hold OK" features need the physical remote to test.
- `am start -W` TotalTime over-reports launcher cold start on this TV (~3.7 s vs ~0.5 s real). Use WaitTime.
- TCL blocks firmware-update checks over ADB (`com.tcl.permission.START_CHECK_SYSTEMUPDATE`): check manually in Settings → About.
- WebView 138 (Aug 2025) and old Spotify builds are outdated on this firmware.

## Open investigations

| Topic | Known so far | Next step |
|---|---|---|
| Slow full boot (power loss) | `adb reboot` → boot_completed ~40 s; 59 processes start in the first 90 s (Play Services, Assistant, Chromecast dex2oat, TCL services) | External stopwatch for bootloader+kernel; split kernel / services / system / launcher |
| Netflix time to a usable catalogue | First frame ~0.9 s (`am start -W` WaitTime, 5 runs). The earlier “3.8 s” was TotalTime; the “~90 s” was the wall time of an `am start` + `screencap` sequence, and `screencap` hangs on the DRM surface, so neither is the app's real load. `uiautomator dump` exposes only the containers, not the catalogue. | Assisted visual test: `scripts/tcl-app-assisted.sh netflix <label> frio`; a person presses any remote key when the catalogue is usable. Repeat cold/warm, right after boot vs stable. |
| "Ok Google" slow / always-on cost | Hotword path costs ≈ 48% of one core continuously | Measure latency of built-in mic vs remote BT mic; test hands-free off (owner decision) |
| Gemini | Button-triggered only, no always-on service, no PC dependency (hosted token service) | Design + cost estimate before any code |
| Android 11 | Only unofficial images (USB flashing) | **Out of scope for now**; revisit only after Android 9 is fully optimized |
