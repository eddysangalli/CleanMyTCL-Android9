# Upstream audit — 2026-10-20

> **Aviso de arquivo histórico (20/10/2026).** Este é um retrato da auditoria naquele horário: SHA de branches, versão instalada, estado dos PRs e decisões posteriores podem ter mudado. A versão do código foi avançada para v0.5.8 e as branches foram sincronizadas depois. Para o estado atual, consulte [BRANCHING](BRANCHING.md), [COMPATIBILITY](../COMPATIBILITY.md) e [RELEASE-0.5.8](RELEASE-0.5.8.md). Não interprete os dados antigos abaixo como estado vivo da TV.

This is a **review record**, not an upstream merge. No upstream pull request was approved,
commented on or merged by this repository.

## Snapshot

| Item | Value |
|---|---|
| Our source base | `9ef40c1` |
| Upstream `main` inspected | `09fe4e4` |
| Divergence | 494 upstream commits since the base; substantial launcher rewrite (`Config`, `Cards`, `HomeKeyService`, themes) |
| Upstream merge batch | 2026-10-20, seen at 09:08 UTC |
| Local validation branch | `develop` (test build 0.5.7, versionCode 19 at the time of that snapshot; the integrated build is now versionCode 29 — see [UPSTREAM-AUDIT-2026-10-20-ROUND2.md](UPSTREAM-AUDIT-2026-10-20-ROUND2.md)); `main` remains v0.5.6 |

A full tree merge was rejected: it would overwrite the TCL-specific navigation, recovery,
accessibility and personalization code before those changes could be reviewed and measured. The
safe ports below are small behavior changes on top of our own files.

## Ported in `develop`

| Upstream PR | Author | Upstream merge | Local commit | Scope and evidence |
|---|---|---|---|---|
| #289 — last visible row no longer nudges the grid up | @Samreay | `a551dc6` | `10b2282` | `requestChildRectangleOnScreen` no longer treats the bottom padding as a reason to scroll. Release build passes. Not benchmarked separately. |
| #291 — Inputs menu shows Settings names and skips hidden inputs | @mwamodo | `6024f36` | `10b2282` | Custom labels and `isHidden()` filtering for the Inputs menu and HDMI number shortcuts. Release build passes; the shared input list was not changed. |
| #292 — Settings key works without HomeKeyService | @Samreay | `ad4cbab` | `10b2282` | Our hook handles `KEYCODE_SETTINGS` directly and reads an **empty** opt-in array for vendor keycodes. The TCL remote's vendor code is not confirmed, so no vendor code is claimed to work. |
| #298 — one tile per app and label | @anandhakrishnanaji | `8c6c3bf` | `1f3ea44` | Deduplicates leanback entries by package + visible label while keeping different labels. Release build and installed smoke test pass. |

The Globoplay redirect settling fix (`1a430e8`) is local work, not an upstream PR. It was confirmed
with the physical button on the TCL BeyondTV2.

## Reviewed but not ported

| Upstream PR | Author | Decision |
|---|---|---|
| #277 — decode wallpaper at screen size, refuse over 4K | @abuhasanrumi | Deferred. Our current wallpaper is bundled; this matters when custom wallpapers (#7) arrive. No measured benefit yet for our path. |
| #280 — tests for configurable grid and sparse dock | @fuglyab | Not ported. Our launcher has no equivalent test harness; adding upstream tests would require a separate refactor. |
| #299 — reorder apps from a top-bar button | @Samreay | Not ported. Optional UX feature, not a performance/default fix; would need its own review and TV test. |
| #305 — optional installed-app library | @harshsingh0930 | Not ported. New screen and data path; optional, outside the preset/performance scope. |
| #293 — rows below dock fits screen; dock title | @Samreay | Not applicable to our fixed dock/grid layout; would redesign the screen. |
| #297 — dock_row option | @Samreay | Not applicable/optional; the dock is a central part of this Home. |
| #275 — leave unused dock slots empty | @abuhasanrumi | Not ported. Our dock currently fills unused slots from the grid; changing that is a product decision, not a bug fix. |
| #221 — optional CPU/memory/storage line | @christosuster | Not ported. Optional stats surface; not needed for the presets. |
| #287 — theme scripts rebind accessibility helper | @Samreay | Not applicable; we have no theme-script system. |
| #288 — reduced motion names below zoomed tile | @Samreay | Already conceptually covered by our light-mode focus treatment; no conflicting port needed. |

## Other upstream changes seen

The same upstream batch also carried translations (Vietnamese `#313`, Finnish `#301`), a
`greeting_name` option (`#172`), Hebrew layout work, a quick-settings panel, festive skins, and
documentation/example updates. They are **not** part of this port: translations need a complete
resource pass, and the visual/theme features are outside the performance-first default. The festive
skin and similar continuous effects are not enabled by any preset.

## Validation limits

- `develop` builds with `assembleRelease`; the resulting 0.5.7 APK is installed only for a smoke
  test. The preset dialog and migration were checked on the TV.
- No upstream behavior change in this table was benchmarked on the TCL BeyondTV2 yet. The old
  v0.5.6 wallpaper numbers are not evidence for these ports.
- #292 needs a physical Settings-button keycode reading before a vendor code is added to
  `settings_key_keycodes`; until then only Android's `KEYCODE_SETTINGS` is handled.
- The working tree contains no upstream credit removal: attribution is in
  [`launcher/NOTICE.md`](../launcher/NOTICE.md), and the upstream MIT license is preserved.

## Next review gate

The roky146 PR queue (#2–#11) is a separate review. None of those PRs is merged or approved here;
#5/#6/#7/#11 remain dependent/open, and #10/#8 stay held until an A/B on this TV and the user's
explicit decision. This audit does not change that status.
