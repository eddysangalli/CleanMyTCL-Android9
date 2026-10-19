# Upstream audit — CleanYourTV vs CleanMyTCL-Android9

Audit date: **2026-10-19**. Compared our `develop` (launcher `br.eddy.tvhome` v0.5.2) against
upstream `Gathin23/cleanyourtv` `main` / release **v2.2.0** (2026-10-19).

Nothing was merged, cherry-picked, installed or flashed on the TV for this audit. All checks that
needed the device were read-only (package state, `dumpsys`, key injection, logcat).

---

## 1. What upstream v2.2.0 actually changed

Upstream moved on: **64 commits** touch `launcher/` between our base (`9ef40c1`) and `v2.2.0`.
Merged on 2026-10-19 10:51: **PR #109** (refresh tiles on package changes), **PR #113**
(`input_action` for HDMI players that ignore `VIEW`, plus keeping the VPN badge visible),
**PR #122** (Ethernet icon on the network pill). v2.2.0 also adds themes via `config.json`,
module cards, a Home-key service, an input-key service, a VPN badge, a Persian calendar and
translations for ca/de/es/fr/ru/th/zh-rCN.

### The launcher was rewritten, not extended

`git diff --stat develop upstream/main -- launcher/app/src/main/java`:

| Upstream v2.2.0 has | Ours (v0.5.2) has |
|---|---|
| `Config.java` (external theme via `config.json`) | `HomePrefs.java` (SharedPreferences only) |
| `Cards.java`, `CardFeature.java` (module cards) | `AppLoader.java` (tile cache) |
| `HomeKeyService.java`, `InputKeyService.java` | `KeyRemapService.java` |
| `HomeSettingsActivity.java` | settings inside `MainActivity` |
| `VpnBadge.java`, `Shamsi.java` | `Weather.java` |
| `AppAdapter.java` | `AppAdapter.java` |

**Consequence: upstream's launcher cannot be merged into ours.** It is a different architecture
(`AppLoader`/`HomePrefs` → `Config`/`Cards`). Anything upstream does is a **port**, not a
cherry-pick. This is the single most important fact for planning.

---

## 2. Corrections and features still missing from ours

| Upstream item | Value for us | Recommendation |
|---|---|---|
| Themes via `config.json` + `tools/theme.sh` | Personalise Eddy TV without rebuilding the APK | **Port later** — needs a `Config` layer; our `HomePrefs` would become a `Config` reader. Medium effort, low risk, and it would make the "Eddy TV" identity shareable. |
| `HomeKeyService` / `InputKeyService` | Only needed on TVs where Home cannot be replaced | **Skip.** Our TV replaces Home successfully. |
| Ethernet icon (PR #122) | Our TV is on Ethernet — useful | **Small port.** Cosmetic, ~1 file, no measurable cost. |
| HDMI `input_action` (PR #113) | Fallback for players that ignore `VIEW` | **Port later.** Our HDMI 1-4 number keys use `TvInputManager` directly and work; `input_action` is the fallback path. Low priority. |
| Module cards (PR #167) | On-TV layout and motion settings | **Partially redundant** — we already have on-TV settings (dock order, hidden apps, ultralight, name, colour). Compare before porting. |
| VPN badge (`VpnBadge.java`) | Not useful on a TV | Skip. |
| Translations ca/de/es/fr/ru/th/zh-rCN | Nice-to-have | **Port the mechanism only if we get contributions.** Note: upstream **deleted** `values-pt-rBR/strings.xml` — see risk 2. |

---

## 3. What we already have that upstream does not

| Our feature | Where | Upstream equivalent |
|---|---|---|
| Tile cache + rebuild only on change | `AppLoader.sameTiles()` | **This is PR #150's main point** — upstream v2.2.0 still calls `reloadApps(true)` on every `onResume()`. We already do it better. |
| Entry animation only on the first build | `MainActivity.showApps()` (`if (first && !light)`) | Same as PR #150's intent |
| "Ultralight" mode, **on by default** | `HomePrefs.light()` (default `true`) | PR #119's `reduced_motion` is **opt-in** — ours is the stronger default |
| HDMI 1-4 number keys | `MainActivity.openHdmi()` | Upstream has no number-key HDMI |
| On-TV settings: name, colour, dock, hidden apps, ultralight | `HomePrefs` + settings UI | PR #167 (open, untested on hardware) |
| Globoplay button redirect | `KeyRemapService` | Upstream has no equivalent |
| Accessibility service scoped to one package | `key_remap.xml` (`android:packageNames`) | **PR #150's point 4 is already done** — upstream would *remove* the global `typeWindowStateChanged` subscription; ours never had one |
| Portuguese (`pt-BR`) launcher strings | `values-pt-rBR/strings.xml` | **Upstream deleted it** (see risk 2) |
| Measured, version-tagged benchmarks | `benchmarks/results/` | Upstream publishes no per-device numbers |
| Per-device protection list | `scripts/protected-packages.txt` | Upstream lists these inside each `debloat.txt` header as prose only |

---

## 4. Security

### Issue #165 — package-name validation (open upstream, **not** fixed in v2.2.0)

`tools/disable.sh` and `tools/restore.sh` in upstream v2.2.0 are **byte-identical to the ones we
inherited**. The vulnerable line is:

```bash
grep -vE '^\s*(#|$)' "$LIST" | sed -E 's/\s+#.*$//' | awk '{print "pm disable-user --user 0 " $1}'
```

`awk` takes the first whitespace-separated field and concatenates it into a shell command that
`tools/tv.py --batch` sends to the device via `dev.shell()`. A list line such as

```
com.tcl.bi;rm -rf /sdcard
```

becomes `pm disable-user --user 0 com.tcl.bi;rm` — the `;rm` is executed **on the TV**. The same
holds for `` com.tcl.bi`id` `` and `com.tcl.bi$(id)`.

**This applies to us: we ship those same `tools/` scripts.**

**What we did (this audit, on our `develop`):**

- New `scripts/tcl-pkg-validate.sh` — validates a package name against the Android naming rules
  (segment starts with a letter; only letters, digits, dot and underscore; ≤ 255 chars; **no
  newline/CR**, which `grep -E` would otherwise let through because it is line-oriented).
- New `tests/test_pkg_validate.sh` — **29 automated tests**, all passing. They cover the injection
  forms above and confirm the project's own `debloat.txt` still validates.
- `tools/disable.sh`, `tools/restore.sh`, `tools/disable.ps1`, `tools/restore.ps1` now **refuse to
  run** on a list with an invalid name, instead of building a command from it.
- `tools/restore.sh` / `tools/restore.ps1` also validate the stock-launcher argument, which came
  from the command line and had the same exposure.
- `scripts/tcl-pkg.ps1` was already safe by design: it whitelist-checks the name against the
  packages actually installed on the TV (`tcl-pkg.ps1:21`), so an unknown name is rejected before
  any command is built. Our primary workflow (`scripts/tcl-try-disable.sh`) goes through it.

**Not done:** the `tools/disable.sh` path resolution fix from PR #153 was applied too (the list
path is now resolved against the caller's directory before the script changes directory), because
it is the same file and the same class of bug. It is called out separately so it is easy to drop if
we would rather keep our `tools/` byte-identical to upstream.

### Issue #164 — ADB over the network stays exposed (open upstream)

Upstream asks for a documented closing step. Added to
[`docs/RECOVERY.md`](RECOVERY.md): how to switch *Network debugging* off on the device, `adb
kill-server` / `adb disconnect` from the computer, how to verify port 5555 is closed
(`Test-NetConnection`), and how to revoke the authorisation stored on the TV.

**Important:** `adb disconnect` only drops this computer's connection. The port stays open on the
LAN until *Network debugging* is switched off **on the TV itself**. Both steps are needed.

### Issue #166 — wallpaper decoded at full size (open upstream, **already mitigated** in v2.2.0)

Upstream v2.2.0's `showWallpaper()` decodes with `inSampleSize` downsampling and
`Bitmap.Config.RGB_565`, so a 4K JPEG costs a 1080p bitmap at most, and it catches
`OutOfMemoryError`. The core of the issue is fixed upstream.

Two points for us:

1. **Our v0.5 has no wallpaper feature at all**, so we are not exposed to #166. Banners and icons
   are drawn into fixed-size tiles (`AppLoader.render()`).
2. If we ever port the theme/wallpaper feature, note that upstream calls `showWallpaper()` from
   `onCreate` — i.e. **it decodes on the UI thread**. On a 2 GB TV that is a Home-open jank spike.
   We would decode it on the worker thread instead.

---

## 5. Performance analysis

### PR #150 — skip the rebuild on return to Home (open upstream, stacked on #143 + #149)

The branch is **stacked on the self-update PR (#143) and the Bitcoin-ticker PR (#149)**, exactly as
you suspected. Importing the branch would bring in an updater and a network ticker.

Isolating the performance content, there are four parts:

| PR #150 item | Our status | Action |
|---|---|---|
| Rebuild only when `getChangedPackages()`/the receiver says something changed | **Already have it** — `AppLoader.sameTiles()` compares and returns without calling back; the package receiver marks stale | None |
| Entry animation only on the first build | **Already have it** — `if (first && !light) grid.scheduleLayoutAnimation()` | None |
| `onTrimMemory` drops tiles and the wallpaper when hidden | **Missing** | **Port this.** Small: a `onTrimMemory` override that clears the cache. Expected effect: less RAM held while another app is in front. Cost: one extra rebuild on the next Home press (the tiles are rebuilt off the main thread anyway). Must be measured. |
| Less overdraw: no window background after `setContentView`, no duplicate root background, `forceHasOverlappingRendering=false` on the ambient glow | **Missing** | **Port this.** Purely a rendering change, no new objects, no animation. Expected effect: fewer overdraw layers while the Home is visible. Low risk. Measure p95 frame time. |
| Accessibility services no longer subscribe to `typeWindowStateChanged` | **Not applicable** — our `key_remap.xml` already filters to `android:packageNames="com.globo.globotv"` | None |

So **two of the four perf items are worth porting, and they are the small ones.** We should not
import the branch.

### PR #119 — GPU focus animation + opt-in `reduced_motion` (open)

Upstream makes reduced motion **opt-in**; our "ultralight" mode is **on by default** and already
disables the scroll animation and the springs (`AppAdapter.light`, `MainActivity`). The upstream
change also moves the focus animation to the GPU.

**Recommendation:** do not port the opt-in flag (it would be a regression for us — we would be
adding a toggle to turn *off* something already off by default). The GPU focus animation could be
evaluated later on its own, but our measured key response is already **p50 13 ms / max 17 ms**,
which is at the edge of what a frame can show, so the headroom is small.

### PR #39 — cache and off-main-thread loading (open upstream)

**We already applied it** (commit `26ff571`, imported with PR #39 + PR #55). Upstream still has it
open. Our `AppLoader` caches tiles for the life of the process, loads on a worker thread and
reuses the tile bitmaps.

**Action: none.** Do not re-apply. This is also the honest attribution point for our `NOTICE.md`.

### Measured state of v0.5.2 (2026-10-19 12:20, on the real TV)

| Metric | v0.5.2 | v0.3.3 (previous measurement) |
|---|---|---|
| Key response (key → first reacting frame) | p50 **13 ms**, p95 16, p99 17, max 17 | p50 14 ms, max 20 |
| Full move | p50 631 ms, max 645 | p50 641, max 696 |
| Janky frames | **0.4 %** of 1367 | 16.2 % (normal mode) / 0 % (ultralight) |
| Cold start | p50 **414 ms**, max 483 | p50 514 ms |
| Back to Home | p50 39 ms, max 181 | p50 39, max 236 |
| Idle 60 s | 14.9 % total CPU, **launcher 0 %**, 0.0 fps | — |
| PSS | 23 MB | 20 MB |

The launcher contributes **0 % CPU and 0 frames per second while idle**. Any change must keep it
there.

---

## 6. TCL / Android TV 9 specifics

### PR #72 — crash fix, tested on a TCL BeyondTV (closed **without** merging)

Upstream closed PR #72 without merging it. It contains a **reproduced crash on a TCL BeyondTV
running Android TV 9** — our exact family:

> `IndexOutOfBoundsException` in `RecyclerView.dispatchOnScrollStateChanged`. `focusTile`'s "not
> laid out yet" scroll listener removed itself and requested focus from inside the callback, and
> `onTileFocused` then called `stopScroll()`, which re-entered RecyclerView's listener loop.
> The `stopScroll()` call came from the PR's own Up-navigation fix.

**We reproduced the author's own test on our v0.5.2** (the method from the PR body: 60 chained
`DPAD_DOWN` / `DPAD_UP` key events in one `input keyevent` call, plus 12 Right/Left, 3 rounds):

```
=== rodada 1 ===   OK  nenhum crash/ANR   foco: br.eddy.tvhome  launcher vivo: 946
=== rodada 2 ===   OK  nenhum crash/ANR   foco: br.eddy.tvhome  launcher vivo: 946
=== rodada 3 ===   OK  nenhum crash/ANR   foco: br.eddy.tvhome  launcher vivo: 946
RESULTADO: sem crash em 3 rodadas (60 Down + 60 Up + 12 Right/Left cada)
```

We also ran a held-button variant (`input keyevent --longpress 20/19`, 6 rounds) — no crash, the
launcher process stayed alive.

**Why we are not affected:** the amplifier was `stopScroll()`, which PR #72's own Up-navigation
fix introduced. **Neither our v0.5 nor upstream v2.2.0 calls `stopScroll()`** (verified by grep).
The latent pattern — removing a scroll listener and requesting focus from inside
`onScrollStateChanged` — **is present in our code and in upstream v2.2.0**:

```java
grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
    @Override
    public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
        rv.removeOnScrollListener(this);
        ...
        if (target != null) target.requestFocus();
    }
});
```

**Recommendation: harden it anyway** (cheap, no behaviour change). Move the listener removal and
`requestFocus()` into a `post()` so they run after the callback returns, and only call
`stopScroll()` while a scroll is running. This is the fix PR #72 settled on and it would protect
us if we ever add an Up-navigation fix of our own.

**Caveat, stated honestly:** PR #72's author also says "a real held remote button was not tested".
Key injection is not identical to a physical button, so this is "not reproduced", not "impossible".

### PR #130 — Thomson 32HE5800, TCL-built, Realtek RTD2841, Android TV 9 (open)

Same vendor (TCL-built), same chipset family, same Android version, different market (Spain,
Orange TV preloaded) and a different build (`PPR2.180905.006.A1`). We **did not copy its list** —
we only used its documented *load-bearing* package list as a cross-check against our own.

**That cross-check found a real gap in our `scripts/protected-packages.txt`:** 13 packages that
PR #130 marks as load-bearing or "do not disable" are installed on our TV and were **not** in our
protected list:

```
com.tcl.partnercustomizer         com.tcl.inputmethod.international
com.tcl.common.terminalmanagerplus com.tcl.xian.StartandroidService
com.tcl.copydatatotv               com.tcl.usercenter
com.tcl.useragreement              com.tcl.tvweishi
com.tcl.appmarket2                 com.tcl.ui_mediaCenter
com.google.android.backdrop        com.tcl.versionUpdateApp
com.tcl.esticker
```

All 13 are now in `scripts/protected-packages.txt`, so `tcl-pkg.ps1` refuses to disable them
without `-Force`.

**Second cross-check result:** PR #130 lists `com.tcl.esticker` as safe to disable. On our TV it
**does not stick** — we disabled it on 2026-10-18, it came back enabled that night and stayed
enabled. It is now out of our profile with the reason recorded. Worth telling the upstream author
of #130, since they list it as a valid target.

---

## 7. Features already in our launcher (no port needed)

| Upstream PR | What it adds | Our status |
|---|---|---|
| #152 | Rearrange tiles from the sofa (hold OK → Move) | We have on-TV settings for dock order and hidden apps, but **not** free tile reordering. Optional; would need a new interaction. |
| #167 | On-TV layout and motion settings | **Largely redundant** with our settings screen. |
| #151 | Remote dock and app reordering | Same as #152; pick one if we port. |
| #143 | Self-update from signed GitHub releases | **Skip.** Self-update on a TV is a supply-chain surface; our `RECOVERY.md` covers reinstalling a tagged version. |
| #156 | Optional weather line above the date | **We already have it** — `Weather.java` + `MainActivity.refreshWeather()`, off by default, never fetches in ultralight mode. |
| #149 | Bitcoin ticker | **Skip.** Needs network, no value on a TV, and it is what PR #150 is stacked on. |

---

## 8. Risks and conflicts

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| 1 | **Upstream's launcher is a rewrite.** Any "merge upstream" is a port, not a merge. | High | Treat upstream as a **source of ideas and small patches**, never as a merge target. Port one thing at a time, behind a branch, with before/after numbers. |
| 2 | **Upstream deleted `values-pt-rBR/strings.xml`.** A future merge would silently drop our Portuguese launcher strings. | High | Keep the translation outside the merge path; check it after every upstream touch. We removed it from the merge surface by not merging the launcher wholesale. |
| 3 | **PR #150's branch is stacked** on #143 (self-update) and #149 (Bitcoin). Importing it imports network features. | Medium | Never import the branch. Take the two small perf items by hand (see §5). |
| 4 | `onTrimMemory` would free tiles while another app is in front → one extra rebuild on the next Home press. | Medium | Measure back-to-Home p50/p95 and cold start before keeping it. Revert if the Home feels slower. |
| 5 | A wallpaper/theme port would decode on the UI thread (upstream's behaviour). | Medium | If we port themes, decode the wallpaper off the main thread and keep the `inSampleSize` limit. |
| 6 | Upstream v2.2.0's `tools/` are byte-identical to ours **except** for the validation and path fixes we just added. | Low | Document our delta in `NOTICE.md` and offer the validation fix upstream as a PR (it fixes #165 and #153 together). |
| 7 | Our `benchmarks/` numbers are device-specific and are **not** comparable to upstream's, which publishes none. | Low | Keep the version tagging in `benchmarks/results/`; never present our numbers as general. |
| 8 | The stock TCL launcher is disabled on our TV. If our launcher is uninstalled, Home is gone until re-enabled by ADB. | Known | Already documented in `RECOVERY.md`; `com.google.android.tvlauncher` is in `protected-packages.txt`. |

---

## 9. Recommended order of work

| # | Item | Why now | Cost | Risk |
|---|---|---|---|---|
| 1 | **Package-name validation** (done, this audit) | Fixes a real command-injection path in the scripts we ship | Done | None — tests pass, no TV change |
| 2 | **Protected list cross-check** (done, this audit) | 13 load-bearing packages were unprotected | Done | None |
| 3 | **ADB shutdown docs** (done, this audit) | Closes upstream #164 | Done | None |
| 4 | **Harden the scroll-listener focus path** | Removes a latent crash class that upstream's own PR #72 hit on our hardware family | ~15 lines, one file | Low — behaviour-preserving |
| 5 | **Port PR #150's overdraw reduction** | Fewer layers while Home is visible; no new objects | Small | Low — measure p95 frame time |
| 6 | **Port PR #150's `onTrimMemory`** | Less RAM held while another app is in front | Small | Medium — extra rebuild on return; measure |
| 7 | **Ethernet badge** (PR #122) | Our TV is on Ethernet | 1 file | None |
| 8 | **Themes via `config.json`** | Makes "Eddy TV" shareable without rebuilding | Medium — needs a `Config` layer | Medium |
| 9 | **Tile reordering from the remote** (PR #152) | Only if someone asks for it | Medium | Low |

**Rule for every item above:** before/after numbers from `scripts/tcl-launcher-bench.sh` and
`scripts/tcl-coldstart.sh`, on the real TV, with the same repetitions. If p95 or max gets worse,
revert. Items 1-3 need no measurement because they change no user-visible behaviour.

---

## 10. Test plan (before/after)

For any launcher change (items 4-8):

```bash
export TCL_TV_HOST=<tv-ip>

# 1. baseline — record it, do not compare against a number from memory
bash scripts/tcl-launcher-bench.sh before-<change> 36 10

# 2. build and install
bash scripts/tcl-launcher-install.sh

# 3. after
bash scripts/tcl-launcher-bench.sh after-<change> 36 10

# 4. functional gate — must be 24/24
bash scripts/tcl-smoke.sh

# 5. reboot gate
bash scripts/tcl-reboot-test.sh
```

**Acceptance criteria (all of them):**

- Key response p50 **≤ 14 ms** and max **≤ 20 ms**
- Back to Home p50 **≤ 45 ms**, max **≤ 200 ms**
- Janky frames **≤ 1 %**
- Idle CPU: launcher still **0 %**, 0.0 fps
- PSS **≤ 25 MB**
- Smoke test 24/24, reboot test clean
- No new package, service, permission or network call in the manifest

Compare `medicoes/launcher/` between the two runs (git-ignored, stays local).

---

## 11. Sources

- Upstream: <https://github.com/Gathin23/cleanyourtv> (`main` @ `8a8249f`, tag `v2.2.0`)
- PRs: #39, #66, #72, #109, #113, #119, #122, #130, #143, #149, #150, #151, #152, #153, #156, #167
- Issues: #164, #165, #166
- Our measurements: `benchmarks/results/tcl-beyondtv2-v660.md` (v0.5.2, 2026-10-19 12:20)
- Device: TCL BeyondTV2, `TCL/BeyondTV2/BeyondTV:9/PPR1.180610.011/AP08:user/release-keys`,
  `ro.board.platform=rtd285o`, Android 9 (API 28), 32-bit, 2 GB RAM, 800 MB zRAM
