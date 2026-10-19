# Contributing a TCL device profile

Profiles live in `devices/<brand>-<model>-<chipset>-<firmware>/`. Copy the BeyondTV2 folder as a template.

## Steps

1. Identify the TV (paste in the profile **without** serial numbers or MAC addresses):
   ```bash
   adb shell getprop ro.build.fingerprint
   adb shell getprop ro.product.model
   adb shell getprop ro.board.platform
   adb shell getprop ro.build.version.sdk
   ```
2. Back up: `scripts\tcl-backup.ps1` (stays local).
3. Baseline: `bash scripts/tcl-bench.sh baseline 3` and `bash scripts/tcl-smoke.sh`.
   Adapt the smoke test's remote name / HDMI input to your TV (see the variables at the top).
4. Disable **one** package at a time with `tcl-try-disable.sh`. Record what passed and what broke.
5. Optional: install the launcher (`tcl-launcher-install.sh`), keep the stock Home enabled, run
   `tcl-launcher-bench.sh` and `tcl-reboot-test.sh`.
6. Fill the matrix row for your device in `COMPATIBILITY.md`, feature by feature (ADB, debloat,
   launcher, HDMI, remote, voice, reboot, standby…). Use "Not tested" freely — it is useful information.
7. Open a PR with `README.md`, `debloat.txt`, and curated numbers (no raw dumps).

## Before opening the PR

Run `powershell -File tools\secret-scan.ps1` and remove anything it flags (IPs, serials, MACs, accounts).
