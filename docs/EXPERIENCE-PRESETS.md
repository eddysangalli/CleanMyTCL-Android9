# Experience presets and measured cost

Status: **develop 0.5.7, test/validation only**. The published reference remains **v0.5.6 on
`main`**. Nothing here was published as a release.

The project rule is:

> **Performance by default. Personalization by choice. Transparency about cost.**

The default experience must stay fast on the TCL BeyondTV2 (Android 9, Realtek RTD2851, 2 GB).
Optional visual features may cost more GPU/RAM or add jank, but they are opt-in, reversible and
must state what is known. Stability, crashes/ANRs, remote/HDMI/audio/app compatibility and
recovery are never optional.

## The three presets

| Preset | Motion | Background | What is known |
|---|---|---|---|
| **Performance** (default on a new install) | Instant focus; no spring | Black | Removes the spring animation and releases the wallpaper bitmap. Not separately benchmarked in v0.5.6 |
| **Balanced** | Smooth focus; current spring | Black | No wallpaper decode. Uses the same current spring as Visual; the shorter Material 3 spring is not integrated yet |
| **Visual** | Smooth focus; current spring | Aurora wallpaper | Measured on the TCL BeyondTV2 with **v0.5.6**: +9 MB PSS, delayed frames **0.19% → 6.89%**, median key response **17 ms** on both sides |

The three presets are the same app and components. They set the existing motion and wallpaper
preferences; they do not create separate launchers, packages or services.

## Measurement provenance

The wallpaper A/B was run on the TCL BeyondTV2 with v0.5.6 using the published protocol
([methodology](../benchmarks/METHODOLOGY.md)) and at least five repetitions. The comparison used
a 108-key navigation battery, with black on one side and Aurora on the other:

| Metric | Black | Aurora |
|---|---:|---:|
| Key response p50 | 17 ms | 17 ms |
| Spring p50 | 633 ms | 635 ms |
| Pooled delayed frames | 0.19% | 6.89% |
| PSS | 23 MB | 32 MB |

Raw data and the exact conditions: [2026-10-19-v0.5.6-wallpaper.md](../benchmarks/results/2026-10-19-v0.5.6-wallpaper.md).

These numbers belong to **v0.5.6**. They are shown in the v0.5.7 UI as historical context with
that label. They will not be reused as v0.5.7 results; a new battery must be run before claiming a
new number.

## Migration and individual options

- A **new install** starts in Performance: instant focus, black background, no wallpaper decode.
- An **upgrade** keeps the existing motion and wallpaper values. The experience is shown as
  **Custom** until the viewer chooses a preset, so an update never changes the look by itself.
- Changing motion or wallpaper individually marks the experience **Custom**.
- Loading a family profile also marks it **Custom**, because a profile carries its own wallpaper
  flag.
- **Restore home screen defaults** returns to Performance.

The wallpaper is currently only shown with smooth motion. Enabling Aurora therefore switches to
**Visual** after showing the measured cost. The combination "Aurora + instant motion" is not
validated on this TV and is not offered as a preset yet.

## Visible cost notices

- The **Experience** dialog explains each preset. Visual carries a short measured note
  (`v0.5.6: +9 MB, 0.19% → 6.89%`).
- The **Wallpaper** action shows the fuller v0.5.6 cost before enabling Aurora; turning it off is
  immediate.
- Options without a valid measurement say so instead of inventing a number. In particular, no
  performance claim is made for the Visual preset on v0.5.7 until it is measured.

## Not integrated yet

- **Expressive Material 3 spring** (`roky146` PR #10): the author reported a shorter spring on a
  different TCL UnionTV. It has not passed our five-repetition A/B on this TV, so Balanced/Visual
  keep the current spring. It can become an optional experimental choice after measurement.
- **BlurHash wallpapers** (PR #8) and additional wallpapers (PR #7): not integrated; no proof yet
  that they remove the measured Aurora jank.
- **Wallpaper at reduced resolution**, overdraw reductions and bitmap-cache changes: candidates
  for a future A/B. Do not change the visual result without the comparison.

## Undo

Choose **Performance** in Home options or **Restore home screen defaults** in the recovery menu.
This returns to the black background and releases the wallpaper bitmap. It does not disable
packages, change firmware or touch the TCL services.
