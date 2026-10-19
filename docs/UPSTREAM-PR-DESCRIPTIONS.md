# Pull request descriptions — prepared, not submitted

Two PRs to <https://github.com/Gathin23/cleanyourtv>, prepared on local branches. **Neither has
been opened, and no pull request has been sent.** Both wait for approval.

| PR | Branch (in this repo) | Base | Files |
|---|---|---|---|
| 1 — TCL BeyondTV2 device profile | `pr/upstream-tcl-beyondtv2-latin-america` | `upstream/main` @ `8a8249f` (v2.2.0) | `examples/tcl-beyondtv2-latin-america/debloat.txt` (new), `examples/README.md` (+1 row) |
| 2 — CleanMyTCL in SHOWCASE | `pr/upstream-showcase-cleanmytcl` | `upstream/main` @ `8a8249f` (v2.2.0) | `SHOWCASE.md` (+1 row) |

**Both branches are based on `upstream/main`, not on our `develop`.** Upstream's `main` is the
default branch there, so the diff shows only what the PR adds — our TCL README, scripts and
device profiles do not appear as noise.

### How to submit them

Upstream asks for the PR to come from a **fork of upstream**, so a contributor does not push to
someone else's repository. The two ways:

1. Fork `Gathin23/cleanyourtv` on GitHub, add the fork as a remote, push the branch, and open the
   PR from the fork.
2. Open the PR from a branch pushed to our own repository, using the "compare across forks" link.

Option 1 is the one upstream's CONTRIBUTING expects.

---

## PR 1 — examples: TCL BeyondTV2 (Latin America / Brazil) debloat list

**Title**

```
examples: TCL BeyondTV2 (Latin America / Brazil) debloat list
```

**Body**

```markdown
Adds `examples/tcl-beyondtv2-latin-america/debloat.txt` and a row in `examples/README.md`.

- **TV:** TCL BeyondTV2 (`ro.product.model` BeyondTV2, brand TCL), Latin America / Brazil market.
  Realtek RTD2851 family (`ro.board.platform` = `rtd285o`), firmware `V8-R851T02-LF1V660`,
  client `TCL-LA-RT2851-S1`.
- **OS:** Android TV 9 (API 28), build `PPR1.180610.011`, security patch 2021-01-05, 32-bit.
- **Hardware:** 2 GB RAM with 800 MB zRAM, eMMC with 10 GB `/data`.
- **Fingerprint:** `TCL/BeyondTV2/BeyondTV:9/PPR1.180610.011/AP08:user/release-keys`

### What it disables

Two packages, both TCL telemetry/content:

| Package | What it does |
|---|---|
| `com.tcl.bi` | analytics / telemetry service |
| `com.tcl.tv.tclhome_passive` | pushes TCL content to the home screen at boot |

### Why this is not the same as `examples/tcl-beyondtv`

Same build number (`PPR1.180610.011`), different product name and different region. The India
set ships Jio/Airtel/Hotstar; this Latin America set instead has a set of UK Freeview catch-up
apps (`uk.co.freeview.*`, 14 of them) plus `au.com.stan.and`, `com.amazon.amazonvideo.livingroom`,
`com.linecorp.linetv` and `tv.wuaki.apptv` — **all already disabled at the factory on this
firmware**, so they need no action and are listed in the file header.

### Testing

Applied on a real unit on 2026-10-18/19. Each package was disabled **on its own**, the device was
rebooted, and a 24-check smoke test passed after every change (HDMI input opens and gets a live
session, Live TV app, Netflix, Google Assistant, microphone, paired Bluetooth remote, audio output,
no crashes or ANRs).

I have **not** lived with this list for days yet, and the file header says so.

**Measured effect: no measurable speed gain.** The two packages removed processes, but idle CPU
and app-open times stayed within run-to-run noise. They are kept for less telemetry, not for
speed — the file header says that too, so nobody expects a speed-up from it.

### Two findings worth recording

- **`com.tcl.esticker` does not stick on this firmware.** It was disabled on 2026-10-18, came back
  enabled the same night and stayed enabled. It is therefore *not* in the list, with the reason in
  the header. (For reference, `examples/thomson-32he5800` lists it as a valid target; on this TCL
  it does not hold. It may be worth a note in that profile too.)
- The list is deliberately short. `com.tcl.waterfall.overseas`, `com.tcl.tvweishi`,
  `com.tcl.notereminder`, `com.tcl.appmarket2` and the Google recommendation rows are installed on
  this unit but were **not** disabled here, so this profile does not claim them. The header lists
  them as "not in this list, with the reason".

### Launcher

Not included, because it is specific to this TV — the same call as the India profile made. The
stock launcher is `com.google.android.tvlauncher` and Live TV is `com.tcl.tv`, so the defaults in
`tools/restore.sh` and `launcher/build-and-install.sh` work unchanged.

The load-bearing packages are listed in the file header with the reason for each: `com.tcl.tv`,
`com.tcl.tvinput`, the remote/BT stack, the key handling, the voice stack, Chromecast, and the
firmware update apps.
```

**Files changed**

```
 examples/README.md                               |  1 +
 examples/tcl-beyondtv2-latin-america/debloat.txt | 58 +++++++++++++++++++++++++
 2 files changed, 59 insertions(+)
```

---

## PR 2 — SHOWCASE: CleanMyTCL-Android9

**Title**

```
SHOWCASE: CleanMyTCL-Android9, a fork specialised for TCL on Android TV 9
```

**Body**

```markdown
Adds one row to `SHOWCASE.md`.

| Project | What it is | Built on | By |
|---|---|---|---|
| [CleanMyTCL-Android9](https://github.com/eddysangalli/CleanMyTCL-Android9) | A fork specialised for TCL televisions on Android TV 9: per-model device profiles with a protected-package list, a measured launcher (ultralight mode, on-TV settings, HDMI number keys), reversible debloat scripts, and published per-device benchmarks with the method and the launcher version each number came from | TCL BeyondTV2, Realtek RTD2851, Android TV 9 | [eddysangalli](https://github.com/eddysangalli) |

![CleanMyTCL-Android9 home screen](https://raw.githubusercontent.com/eddysangalli/CleanMyTCL-Android9/develop/docs/assets/eddy-home.png)

It is a fork of this repository, and the repository keeps the history up to `9ef40c1` and builds
on it. What it changes:

### What it adds on top of the shared launcher

- **Ultralight mode, on by default.** No scroll animation, no springs, no glow, no tile title
  and no entry animation: the focus jumps instead of gliding (1 frame drawn per move against
  38 in normal mode). The greeting, the weather line and the dock are still there — what the
  mode changes is the motion, not the content.
- **Everything configurable from the sofa.** Name, system name, accent colour, dock order, hidden
  apps and the ultralight toggle are all edited on the TV. No "Eddy" is hard-coded — the
  identity is a configuration, so the build is usable by anyone.
- **HDMI number keys.** Keys 1-4 jump straight to HDMI 1-4 from Home, in the same order as the
  Inputs menu.
- **Globoplay button redirect.** On this TCL the remote's Globoplay key is injected by TCL's own
  software (scanCode 0), so it never reaches a key filter. The fork watches for Globoplay's entry
  screen instead and swaps it for the app you chose. The accessibility service is scoped to that
  one package, so it is not woken on every window change.
- **Per-model profiles** with a **protected-package list** — the scripts refuse to disable the
  packages that carry HDMI, the tuner, the remote, the voice stack, DRM, Play and Cast, without
  an explicit override.

### What it adds outside the launcher

- **Device profiles** per model (`devices/<model>-<chipset>-<firmware>/`), each with the debloat
  list, what was tested, what was not, and the load-bearing packages.
- **Reversible debloat scripts** that disable one package at a time, run a 24-check smoke test
  and revert automatically if anything breaks.
- **Published benchmarks with the method attached** and, for every number, the exact launcher
  version it was measured on. Numbers are never carried over between versions.

### Hardware it was built and tested on

TCL BeyondTV2 — Realtek RTD2851 family (`ro.board.platform` = `rtd285o`), firmware
`V8-R851T02-LF1V660`, Android TV 9 (API 28), 32-bit, 2 GB RAM with 800 MB zRAM.
Fingerprint `TCL/BeyondTV2/BeyondTV:9/PPR1.180610.011/AP08:user/release-keys`.

### Measured on that TV (launcher v0.5.2, 2026-10-19)

| Metric | Value |
|---|---|
| Key response, key event to first reacting frame | p50 13 ms, p95 16 ms, max 17 ms (36 keys) |
| Janky frames | 0.4 % of 1367 |
| Cold start | p50 414 ms |
| Back to Home | p50 39 ms |
| Idle 60 s | 14.9 % total CPU, **the launcher itself 0 %**, 0.0 fps |
| PSS | 23 MB |

The method, the raw numbers and which version each row belongs to are in the repository.

### Validated versus experimental — kept apart on purpose

**Validated on that TV** (24/24 smoke test, reboot test, benchmarks):

- The launcher install, the Home swap and the way back to the stock launcher
- Ultralight mode, the on-TV settings, the HDMI number keys
- ADB over the network, backup and restore of the package state
- The two-package debloat list in the device profile
- The key-latency, cold-start, back-to-Home and idle measurements above

**Not yet validated, and labelled as such in the repository:**

- Standby resume and power-loss boot with a stopwatch
- Time-to-usable for Netflix, YouTube and Globoplay (Netflix is still under investigation)
- The Globoplay redirect on a held remote button
- The debloat list over days of daily use
- The D-pad crash fix from PR #72: **reproduced with the author's own test** (60 chained
  `DPAD_DOWN`/`DPAD_UP` per round, 3 rounds) on this hardware, with no crash and the launcher
  process staying alive. Key injection is not the same as a physical button, so this is "not
  reproduced", not "impossible".

### About the data

The repository collects nothing, phones home to nothing, and makes no network request from the
launcher except the optional weather line, which is off by default and never fetched in
ultralight mode. There is no analytics, no crash reporting and no update check.

### Relationship to this repository

The fork keeps the history up to `9ef40c1` and adds on top of it. It is registered as an
independent repository on purpose, so that the TCL-specific work and the device profiles are not
tied to upstream's release cycle; the upstream history and credits are intact, and
`NOTICE.md` in the fork lists the origin of every part. Non-TCL-specific improvements are
offered back here.

Two contributions are already prepared for this repository and not yet submitted: a device
profile for the TCL BeyondTV2 (Latin America) and the package-name validation for the debloat
tools.
```

---

## What is deliberately not in either PR

- **No benchmark numbers in the device profile.** The profile file is a debloat list; the numbers
  live in the fork's own repository. Upstream's `examples/` files are debloat lists, and the
  upstream project deliberately does not publish per-device performance claims.
- **No launcher code in either PR.** PR 1 is a text file and a table row. PR 2 is a table row.
- **No mention of the "several days" claim.** Both the profile file and the PR body state plainly
  that the packages were verified over a single session across reboots, not over days.
- **No claim that the fork is a drop-in replacement.** The fork's launcher is on a different
  architecture from upstream v2.2.0 (`AppLoader`/`HomePrefs` against `Config`/`Cards`), so the
  fork says so in its own README.
