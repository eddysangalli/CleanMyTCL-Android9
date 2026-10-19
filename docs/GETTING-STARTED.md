# Getting started on Windows 11

## 1. Install the tools

- **ADB**: `winget install Google.PlatformTools` (or Android SDK platform-tools). Check with `adb version`.
- **Git for Windows** (provides Git Bash, used by `scripts/*.sh`).
- To build the launcher only: JDK 17 and Android SDK (API 34 build tools). Set `JAVA_HOME`, `ANDROID_HOME`.

## 2. Enable ADB on the TCL (Android TV 9)

1. Settings → Device Preferences → About → click **Build** 7 times ("You are a developer").
2. Settings → Device Preferences → Developer options → **USB debugging** / **Network debugging** on.
3. Find the TV IP: Settings → Network → your connection.
4. On the PC: `adb connect <tv-ip>:5555` and accept the prompt **on the TV** ("Always allow").

Set the IP once per terminal:

```powershell
$env:TCL_TV_HOST = "<tv-ip>"          # PowerShell
export TCL_TV_HOST=<tv-ip>             # Git Bash
```

## 3. Identify your TV

```bash
adb shell getprop ro.build.fingerprint
adb shell getprop ro.product.model
```

Compare with [COMPATIBILITY.md](../COMPATIBILITY.md). If there is no matching profile, everything below is
**untested on your TV**: go slower and share your results ([devices/README.md](../devices/README.md)).

## 4. Diagnose (read-only)

```bash
bash scripts/tcl-audit.sh          # CPU since boot, alarms, versions (writes to auditoria/, git-ignored)
bash scripts/tcl-smoke.sh          # essential functions; exit code 1 if anything fails
```

`tcl-smoke.sh` opens Settings, an HDMI input, Netflix and the Assistant; run it when nobody is watching.

## 5. Back up the state

```powershell
powershell -ExecutionPolicy Bypass -File scripts\tcl-backup.ps1
```

Saves the package list, disabled packages, Home resolution and settings in `backup/<timestamp>/`
(git-ignored: it contains serial numbers). Point `backup/BASELINE.txt` at the snapshot you want to return to.
This is **not** a firmware backup and does not save app data.

## 6. Apply optimizations — one at a time

```bash
bash scripts/tcl-try-disable.sh <package>     # disables, runs the smoke test, reverts automatically on failure
bash scripts/tcl-bench.sh after-<package> 3   # measure; compare with your baseline in medicoes/historico.csv
```

Keep a change only if the benchmark shows a gain and the smoke test passes.

## 7. Install the launcher (without touching the Home yet)

```bash
bash scripts/tcl-launcher-install.sh          # builds launcher/ (release) and installs it
```

Press Home on the remote: Android asks which Home to use; choose the new one. Only after a few days
of use (and a reboot test) consider disabling the stock launcher. See [RECOVERY.md](RECOVERY.md) first.

## 8. Going back

```powershell
powershell -ExecutionPolicy Bypass -File scripts\tcl-restore.ps1          # dry-run
powershell -ExecutionPolicy Bypass -File scripts\tcl-restore.ps1 -Apply   # re-enable everything changed since baseline
```
