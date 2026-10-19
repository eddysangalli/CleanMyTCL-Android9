# Getting started on Windows 11

## 1. Install the tools

- **ADB**: `winget install Google.PlatformTools` (or Android SDK platform-tools). Check with `adb version`.
- **Git for Windows** (provides Git Bash, used by `scripts/*.sh`).
- To build the launcher only: JDK 17 and Android SDK (API 34 build tools). Set `JAVA_HOME`, `ANDROID_HOME`.

## 2. Enable ADB on the TCL (Android TV 9) — Detailed Guide

### Method A: Network ADB (recommended, no cable needed)

> Works on TCL BeyondTV2 and most TCL Android TV 9 models. Requires TV and PC on the same LAN (Wi-Fi or Ethernet).

#### Step-by-step

1. **Open Developer Options**
   - Press **Home** on remote → **Settings** (gear icon) → **Device Preferences** → **About**
   - Scroll to **Build** and press **OK/Select 7 times quickly**
   - You'll see: *"You are now a developer!"* (or "You are a developer")

2. **Enable Network Debugging**
   - Go back to **Device Preferences** → **Developer options**
   - Turn **ON**: **Network debugging** (some firmware: "ADB debugging" + "Network debugging")
   - Turn **ON**: **USB debugging** (required on some builds even for network)
   - **Optional but recommended**: Turn **ON** "Disable ADB authorization timeout" (keeps authorization persistent)

3. **Find the TV IP address**
   - Settings → **Network & Internet** → your connection (Wi-Fi or Ethernet)
   - Note the **IP address** (e.g., `192.168.1.50`)

4. **Connect from Windows**
   - Open **PowerShell** or **Git Bash**
   - Run:
     ```powershell
     adb connect 192.168.1.50:5555
     ```
   - **On the TV screen**: a dialog appears — **"Allow USB debugging?"** → check **"Always allow from this computer"** → **OK**

5. **Verify**
   ```powershell
   adb devices
   # Should show: 192.168.1.50:5555  device
   ```

6. **Persist the IP (optional but recommended)**
   ```powershell
   # PowerShell
   $env:TCL_TV_HOST = "192.168.1.50"
   # Git Bash
   export TCL_TV_HOST=192.168.1.50
   ```
   > Add to your shell profile (`$PROFILE` or `~/.bashrc`) to make it permanent.

---

### Method B: USB ADB (if network fails)

> Requires USB-A to USB-A (or USB-C) cable; TCL service port is often hidden.

1. Enable **USB debugging** in Developer Options (same as above)
2. Connect TV ↔ PC via USB (try service port or USB 2.0 port)
3. On PC: `adb devices` → authorize on TV
4. Use `adb shell` directly (no IP needed)

---

### Method C: Hidden Service Menu (if Developer Options missing)

> Some carrier/region firmwares hide Developer Options.

1. Press: **Settings** → **Device Preferences** → **About**
2. Press **OK** on **Build** 7 times
3. If still missing, try remote sequence: **Home** ×5 → **Up** ×3 → **Right** ×2 → **OK** (opens hidden menu on some TCL)
4. Or: Install "ADB Enabler" apps from Play Store (requires sideload first)

---

### Troubleshooting ADB Connection

| Symptom | Fix |
|---------|-----|
| `connection refused` | TV and PC on same subnet? Wi-Fi isolation (AP isolation) off? |
| `unauthorized` | Accept prompt on TV; revoke in Developer Options → "Revoke USB debugging authorizations" and retry |
| `offline` | `adb kill-server && adb start-server`; reboot TV |
| Port 5555 not open | Re-enable Network debugging; some firmwares need reboot after enabling |
| "Developer options" not visible | Method C; or check Settings → Apps → Show system → "Android TV Core Services" → enable |

---

### Security Notes

- **Network debugging** exposes ADB on LAN port 5555. Only use on trusted home network.
- **Revoke authorizations** when done: Developer Options → "Revoke USB debugging authorizations"
- **Disable Network debugging** when not actively developing
- This project's scripts require `$env:TCL_TV_HOST` — they **do not** hardcode IPs

---

## 3. Identify your TV

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
