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
| Netflix ~90 s to usable catalogue | First frame in 3.8 s; network ruled out for the analysed boot; no low-memory kills | Screenshot series: right after boot vs 10 min vs stable, cold vs warm, 3 reps each; separate process start, auth, DRM, catalogue |
| "Ok Google" slow / always-on cost | Hotword path costs ≈ 48% of one core continuously | Measure latency of built-in mic vs remote BT mic; test hands-free off (owner decision) |
| Gemini | Button-triggered only, no always-on service, no PC dependency (hosted token service) | Design + cost estimate before any code |
| Android 11 | Only unofficial images (USB flashing) | **Out of scope for now**; revisit only after Android 9 is fully optimized |
