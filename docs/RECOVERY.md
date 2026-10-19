# Recovery

Restoring **settings and package states** is not a firmware backup. Nothing here wipes app data or
factory-resets the TV.

| Situation | Fix |
|---|---|
| Re-enable packages disabled by our scripts | `scripts\tcl-restore.ps1 -Apply` (compares with `backup/BASELINE.txt`) or `adb shell pm enable <pkg>` |
| Get the stock Home back | `adb shell pm enable com.google.android.tvlauncher` then press Home and choose it (or `adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity`) |
| New launcher crashes / black screen | `adb shell pm enable com.google.android.tvlauncher`; then `adb shell pm disable-user br.eddy.tvhome` (or your build's id). Without ADB: the TCL key service has a `FallbackHome`; Settings is reachable from the remote's settings key |
| Restore an older launcher version | Android refuses downgrades over an install. Check out the tag (`git checkout launcher-vX.Y`), bump `versionCode` in `launcher/app/build.gradle`, build and install. Or `adb uninstall` then install the old APK (loses launcher settings only) |
| Undo performance settings | Each change in `OPTIMIZATION-CANDIDATES.md` lists its undo command; `settings put global animator_duration_scale 1.0` etc. |
| Turn off the Globoplay redirect | `adb shell settings put secure enabled_accessibility_services ""` |
| See what changed after a TCL/Play update | Take a new `tcl-backup.ps1` snapshot and diff `packages_*.txt` and `home_resolvers.txt` against the baseline |
| Apps you uninstalled yourself | Reinstall from Play Store, or `scripts/tcl-cleanup-apps.sh reinstall <pkg>` if its APK was saved first |

**Order of safety:** never disable the stock launcher before the new one is installed, set as Home,
smoke-tested and survived a reboot (`scripts/tcl-reboot-test.sh`).

---

## Closing network debugging when you are done (upstream issue #164)

Network debugging leaves the TV's ADB port **5555 open on the local network**. Anyone on the same
Wi-Fi can run `adb connect <tv-ip>:5555` and, once authorised, control the TV. Turn it off when
development stops.

### On the TV (the authoritative way)

1. **Settings → Device Preferences → Developer options**
2. Turn **OFF** *Network debugging* (and *ADB debugging* if you no longer need USB)
3. Leave *USB debugging* off unless you still use a cable

### From the computer (does not need the TV to be reachable)

```powershell
# stop the local ADB server and forget the device
adb kill-server
adb disconnect 192.168.1.8:5555
```

`adb disconnect` only drops this computer's connection. The TV keeps listening on 5555 until you
switch *Network debugging* off on the device itself, so **do both**.

### Check that the port is really closed

```powershell
Test-NetConnection -ComputerName <tv-ip> -Port 5555
# TcpTestSucceeded : False  -> closed
```

### If the TV was authorised from this computer before

The authorisation lives on the device. To drop it: **Developer options → Revoke USB debugging
authorisations**. After that, a new `adb connect` asks for permission again.

### While you are still developing

Keep *Network debugging* on but stay on a trusted home network, and consider disabling
*ADB authorization timeout* so you are not re-approving on every reconnect. See
[GETTING-STARTED.md](GETTING-STARTED.md) for the full ADB setup.
