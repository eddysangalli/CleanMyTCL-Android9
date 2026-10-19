# CleanMyTCL-Android9

> Community-driven Android TV 9 optimization and lightweight launcher project for TCL televisions, with tested device profiles, reversible debloating and performance benchmarks.

🇧🇷 [Leia em português](README.pt-BR.md)

**Fork of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (formerly `tv-vibehack`) by Gathin, MIT.**  
CleanYourTV targets any Android/Google TV. This fork narrows the scope to **TCL televisions running Android TV 9**, and adds what we needed to make one of them fast and stay reversible: per-model profiles, a measured launcher, test/benchmark scripts and recovery procedures.  
See [NOTICE.md](NOTICE.md) for credits and [docs/PROVENANCE.md](docs/PROVENANCE.md) for what came from where.

---

## 📸 Screenshots

| Stock TCL Launcher | Eddy TV (CleanMyTCL Launcher v0.5) |
|:---:|:---:|
| ![Stock Home](docs/assets/stock-home.png) | ![Eddy TV Home](docs/assets/eddy-home.png) |
| *Original TCL Home — ads row, slow grid, no shortcuts* | *Eddy TV — greeting by time, HDMI shortcuts, ultralight mode, configurable* |

| Settings on TV | HDMI & Live TV Quick Access |
|:---:|:---:|
| ![Settings](docs/assets/eddy-settings.png) | ![HDMI Shortcuts](docs/assets/eddy-hdmi.png) |
| *All settings accessible from Home — no deep menus* | *Keys 1-4 = HDMI 1-4, 5 = Live TV, long-press = remap* |

| Performance Dashboard | Benchmark Chart |
|:---:|:---:|
| ![Perf](docs/assets/perf-dashboard.png) | ![Benchmarks](https://quickchart.io/chart?c=%7Btype%3A%27bar%27%2Cdata%3A%7Blabels%3A%5B%22Boot%20frio%22%2C%22Standby%22%2C%22Home%22%2C%22D-pad%22%2C%22Netflix%22%2C%22YouTube%22%2C%22Globoplay%22%2C%22Spotify%22%2C%22CPU%20idle%22%2C%22PSS%22%2C%22RAM%20livre%22%2C%22zRAM%22%5D%2Cdatasets%3A%5B%7Bdata%3A%5B92%2C3.2%2C0.8%2C0.04%2C87%2C12%2C15%2C8%2C1.2%2C42%2C520%2C280%5D%2CbackgroundColor%3A%5B%22%23e74c3c%22%2C%22%23e67e22%22%2C%22%23f39c12%22%2C%22%2327ae60%22%2C%22%23e74c3c%22%2C%22%233498db%22%2C%22%239b59b6%22%2C%22%231abc9c%22%2C%22%2327ae60%22%2C%22%2334495e%22%2C%22%232c3e50%22%2C%22%237f8c8d%22%5D%7D%5D%7D%2Coptions%3A%7BindexAxis%3A%27y%27%2Cplugins%3A%7Blegend%3A%7Bdisplay%3Afalse%7D%2Ctitle%3A%7Bdisplay%3Atrue%2Ctext%3A%22Benchmarks%20TCL%20BeyondTV2%22%7D%7D%2Cscales%3A%7Bx%3A%7BbeginAtZero%3Atrue%7D%7D%7D%7D) |
| *Real-time CPU/RAM/zRAM on Home (optional)* | *Generated via quickchart.io — cold boot, app launch, key latency* |

> **Add your screenshots:** Place PNG files in `docs/assets/` with the names above.  
> See [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) for how to capture via `adb exec-out screencap`.

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