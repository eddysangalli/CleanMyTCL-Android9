# CleanMyTCL-Android9

> Community-driven Android TV 9 optimization and lightweight launcher project for TCL televisions, with tested device profiles, reversible debloating and performance benchmarks.

🇧🇷 [Leia em português](README.pt-BR.md)

**Fork of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (formerly `tv-vibehack`) by Gathin, MIT.**  
CleanYourTV targets any Android/Google TV. This fork narrows the scope to **TCL televisions running Android TV 9**, and adds what we needed to make one of them fast and stay reversible: per-model profiles, a measured launcher, test/benchmark scripts and recovery procedures.  
See [NOTICE.md](NOTICE.md) for credits and [docs/PROVENANCE.md](docs/PROVENANCE.md) for what came from where.

---

## 📸 Screenshots

**Real screenshots are temporarily unavailable.** The files previously uploaded under `docs/assets/` were accidentally converted to UTF-16 text, making them invalid PNGs. They have been hidden from this README rather than displaying broken thumbnails or duplicated images.

**[Recapture and restore the gallery (Windows 11 + ADB)](docs/SCREENSHOTS.md)** — includes a binary-safe PowerShell script. Screenshots must be taken from the real TV, visually checked, and committed as binary PNGs before embedding them here.

### Published measurements (not a screenshot)

| Metric | Stock Android TV Home | Eddy TV light launcher v0.1 |
|---|---:|---:|
| Whole-device idle CPU (% of one core) | 57.3% | 15.5% |
| Janky frames (test session) | 85.9% | 7.7% |
| Idle redraw rate | 60 fps | ~0.1 fps |

[Method and other results](benchmarks/results/tcl-beyondtv2-v660.md). These are measured on TCL BeyondTV2/R851T02 V660; **they are not full v0.5 benchmarks**. Netflix time-to-usable and power-loss boot stopwatch measurements remain pending.

---

## Is my TV supported?

**Only one model is validated so far.** Do not assume your TCL works because it also runs Android 9: firmware, chipset and regional app sets differ. Check the [compatibility matrix](COMPATIBILITY.md).

| Device profile | Chipset | Firmware | Status |
|---|---|---|---|
| [TCL BeyondTV2](devices/tcl-beyondtv2-rtd2851-r851t02-v660/) | Realtek RTD2851 | R851T02 V660 (Android 9, API 28, 32-bit, 2 GB) | Partially validated (see matrix) |

---

## Principles

1. **Performance first.** No feature is kept if it makes Home, navigation or app launch slower. Every change is measured (p50 and tail latency) against the previous version.
2. **Reversible.** System apps are only `pm disable-user`'d, one at a time, with a smoke test and an undo command. Nothing is uninstalled from `/system`. No root, no firmware flashing.
3. **Evidence over claims.** Speed numbers come with method, device and raw-data location ([benchmarks/](benchmarks/)). Untested means untested.
4. **The stock launcher is never disabled before the new Home is installed and verified**, and the way back is documented ([docs/RECOVERY.md](docs/RECOVERY.md)).

---

## Repository layout

| Path | What |
|---|---|
| `launcher/` | Lightweight Home (`com.gathin.tvhome` code from upstream + TCL features: ultralight mode, D-pad menus, HDMI shortcuts, voice button, on-TV settings, configurable greeting) |
| `scripts/` | Windows PowerShell + Git Bash tools: backup, restore, safe disable, smoke test, reboot test, benchmarks |
| `tools/` | Upstream CleanYourTV tools (`tv.py`, `disable.*`, `restore.*`) — kept unchanged |
| `devices/` | Per-model profiles: debloat list, protected packages, what was tested |
| `benchmarks/` | Methodology and curated results |
| `docs/` | Setup on Windows 11, ADB, backup/restore, recovery, personalization, known issues, provenance |
| `examples/` | Upstream debloat lists from other brands (reference only) |
| `private/` | Your local data. Ignored by git, never published |

---

## Quick start (Windows 11)

Full guide: [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md).

```powershell
$env:TCL_TV_HOST = "<tv-ip>"                                              # your TV's IP
powershell -ExecutionPolicy Bypass -File scripts\tcl-backup.ps1           # 1. snapshot current state
bash scripts/tcl-smoke.sh                                                 # 2. check essential functions
bash scripts/tcl-try-disable.sh com.tcl.bi                                # 3. disable ONE package, test, auto-revert on failure
powershell -ExecutionPolicy Bypass -File scripts\tcl-restore.ps1          # dry-run of going back to the baseline
```

---

## Relationship with upstream

This repo keeps CleanYourTV's history up to commit `9ef40c1` and branches from there. Upstream has moved on (v2.x, themes, Home-key service). We track it as the `upstream` remote and **cherry-pick** fixes after reviewing conflicts, behavior changes and measured performance impact ([docs/BRANCHING.md](docs/BRANCHING.md)). Improvements that are not TCL-specific should be offered back to CleanYourTV.

---

## Disclaimer

Use at your own risk. Disabling the wrong package can break HDMI, the remote, voice or the Play Store. Always back up first and change one thing at a time. Not affiliated with TCL or Google.

License: MIT ([LICENSE](LICENSE)). Third-party components: [NOTICE.md](NOTICE.md).