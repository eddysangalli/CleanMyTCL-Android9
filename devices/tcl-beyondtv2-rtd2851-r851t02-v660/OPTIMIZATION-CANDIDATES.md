# Optimization candidates — NOT APPLIED (awaiting approval)

Rules: one change at a time, `tcl-bench.sh` before/after (3+ reps, p50 and tail), `tcl-smoke.sh` after,
revert if no measured gain. Already measured as irrelevant and **not to repeat**: disabling more TCL
promo/telemetry packages for speed, system animation scale 0.5x (more janky frames), `speed` dexopt of
the launcher, zRAM/kernel/swap tweaks, RAM cleaners.

| # | Package / setting | Function | Evidence of waste | Dependencies | Expected benefit | Risk | Change | Undo | Validation |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Hands-free "Ok Google" (built-in TV mics) — **setting, not a package** | Always-on hotword via `voice_ap` + `katniss:interactor` | ≈ 48% of one core, 24/7 (audit 2026-10-19: 39 min CPU each in 2 h 25 min uptime) | Assistant, remote mic button must keep working | Up to ~12% of total CPU freed; less contention during app start | Low; loses only voice without the remote | TV mic privacy switch or Assistant setting "Ok Google detection" off (manual, on the TV) | Turn the setting back on | `tcl-hotword-test.sh`, `tcl-idle-probe.sh`, remote mic button test, smoke |
| 2 | `com.google.android.tvrecommendations` | Recommendation rows for the **stock** launcher | Crashes once per boot looking for the disabled stock launcher (`LAUNCH_BOOT_HELPER`) | None known with the new Home | Removes one crash per boot and up to ~15 MB | Low | `bash scripts/tcl-try-disable.sh com.google.android.tvrecommendations` | `adb shell pm enable com.google.android.tvrecommendations` | smoke, reboot test, Play Store/YouTube rows |
| 3 | `appops RUN_IN_BACKGROUND ignore` for Netflix / Spotify | Stops background work of apps not in use | ~2.5% + 1.5% of one core average | **Spotify Connect** may stop working | ~4% of one core | Low-medium | `adb shell cmd appops set com.spotify.tv.android RUN_IN_BACKGROUND ignore` | `... RUN_IN_BACKGROUND allow` | Spotify Connect from phone, Netflix resume |
| 4 | Remaining TCL services (Alexa, T-Cast, TCL account, message box) | Optional TCL features | ~50–70 MB RAM total, little CPU; start at boot (59 processes in first 90 s) | Only if the owner does not use them | Less boot contention (to be measured with `tcl-boot-test.sh`) | Medium | one at a time with `tcl-try-disable.sh` | `pm enable` | boot test with Netflix open N s after Home |
| 5 | Screensaver Backdrop → lighter dream | Idle screensaver | Network + CPU while idle | None | Idle only | None | Settings → Screensaver | Settings | idle probe |
| 6 | `KeyRemapService` (launcher accessibility, Globoplay redirect) | Redirects the Globoplay key | Accessibility services can add key latency; measured without service p50 2 ms, **with service not yet measured** | Launcher v0.5 | Confirm zero cost or disable | Low | `settings put secure enabled_accessibility_services ""` | re-enable in launcher options | `tcl-keypath-bench.sh` with/without |
| 7 | Update WebView (138, Aug 2025) and Spotify (2022) | Security and app fixes | Outdated | Play Store | Unknown, measure | Low | Play Store | uninstall updates | app start test |

Do **not** disable yet (owner's decision 2026-10-19): voice services (`katniss`, `tcl.assistant`,
`micmanager`, `speech.pumpkin`), Chromecast, Play Services, DRM, any `com.tcl.tv*`.
