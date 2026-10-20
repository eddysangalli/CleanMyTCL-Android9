# Experience presets and measured cost

Status: the current repository source on `main` and `develop` is **v0.5.8 (versionCode 30)**. GitHub has no formal v0.5.8 release artifact or validated stable tag. The most recent explicitly identified installed APK in the published device reports is a **v0.5.7 validation build (code 29)**; actual current installation needs a new ADB check. The full v0.5.8 performance/standby/Cast/soak battery remains pending.

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
| **Balanced** | Smooth focus; the current spring | Black | No wallpaper decode. Uses the same spring as Visual |
| **Visual** | Smooth focus; the current spring | Aurora wallpaper | Measured on the TCL BeyondTV2 with **v0.5.6**: +9 MB PSS, delayed frames **0.19% → 6.89%**, median key response **17 ms** on both sides |

The three presets are the same app and components. They set the existing motion and wallpaper
preferences; they do not create separate launchers, packages or services.

### The Material 3 Expressive spring in the 0.5.7/0.5.8 code

The integrated build uses the **Material 3 Expressive spring** from @roky146's PR #10
(stiffness 380, damping ratio 0.8, plus a subtle lean at the grid edges). It replaced the older
spring (stiffness 260, damping 0.58) in the Balanced and Visual presets.

The spring **was compared on our BeyondTV2 in a controlled, single-parameter A/B on a v0.5.7 test build**: median full movement ~441 ms (M3E) versus ~635 ms (old), with median initial response 18 ms in both. See [spring A/B](../benchmarks/results/2026-10-20-v0.5.7-spring-m3e.md). These are not v0.5.8 results, nor a full battery for all experience modes. The
v0.5.6 spring figures quoted below describe the *old* spring and stay labelled as v0.5.6.

Performance (instant focus) is unchanged by this: with no spring, the focus moves in one frame
and the glass has nothing to interpolate.

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

These numbers belong to **v0.5.6** and must remain labelled historical wherever shown in newer versions. They are not results for v0.5.7 or v0.5.8.

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
  immediate. Any other image background (Earth at night, the BlurHash blurs) shows the
  **"not measured on this TV"** notice instead, and a solid colour switches with no notice.
- Options without a valid measurement say so instead of inventing a number. In particular, no
  performance claim is made for the Visual preset on v0.5.7 until it is measured.

## Backgrounds in v0.5.8 source

The background is a list, cycled with Left/Right in the options, and every entry is reversible.
`black` is the default and is what Performance uses.

| Background | What it is | Cost on this TV |
|---|---|---|
| Black | The window's own black; no draw at all | 0 |
| Six solid colours | One full-screen fill (midnight, forest, plum, wine, graphite, petrol), chosen for at least 13:1 contrast with the white header text | One fill; not separately measured |
| Aurora | The bundled 1920×1080 photo, decoded off the main thread at RGB_565 | **Measured in v0.5.6**: +9 MB PSS, delayed frames 0.19% → 6.89% |
| Earth at night | A bundled NASA photo, same decoding | **Not measured on this TV** |
| Three BlurHash blurs | A 32×18 bitmap decoded from a 28-character string; the photo itself is not in the APK | **Not measured on this TV** |

A small file is not a small runtime cost: the BlurHash is tiny on disk, but it is still a
full-screen image drawn every frame, so its cost has to be measured like any other wallpaper.

## Not integrated yet

- **Wallpaper at reduced resolution**, overdraw reductions and bitmap-cache changes: candidates
  for a future A/B. Do not change the visual result without the comparison.
- **The complete v0.5.8 battery**, five matched repetitions for each preset, and the BlurHash/background A/B remain pending. The specific v0.5.7 spring A/B exists; do not relabel it as v0.5.8.

## Undo

Choose **Performance** in Home options or **Restore home screen defaults** in the recovery menu.
This returns to the black background and releases the wallpaper bitmap. It does not disable
packages, change firmware or touch the TCL services.
