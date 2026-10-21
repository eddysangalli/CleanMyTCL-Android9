# Provenance audit (2026-10-19)

Question: is our code a real fork of CleanYourTV? **Answer: yes — Scenario A (direct derivative).**

## Evidence

| Check | Result |
|---|---|
| Java package of the launcher | `com.gathin.tvhome` (upstream author's namespace), unchanged |
| Upstream snapshot used | `ref/tv-vibehack` clone at commit `9ef40c1` ("README: restructure for readers", 2026-10-18), remote `Gathin23/tv-vibehack` |
| Is `9ef40c1` in today's `Gathin23/cleanyourtv`? | **Yes** — same repository renamed; 157 upstream commits since then |
| Private working repo (`launcher/`) | Git, branch `master`, 9 commits `v0.3` → `v0.5`, 8 tags. First commit is a full copy of upstream `launcher/` + PRs #39/#55 + TCL changes (no upstream history was kept) |
| Divergence from `9ef40c1` (launcher `src/`) | 29 files, +1585 / −322 lines. New files: `AppLoader.java` (PR #39), `HomePrefs.java`, `KeyRemapService.java`, `Weather.java`, pt-BR strings, icons |
| Code from other repositories | None found. Other launchers were used as reference for ideas only (documented in `etapa3`/`etapa4` reports). `perf.py` (briansilah fork): methodology only |
| Third-party assets | Outfit font (SIL OFL 1.1, from upstream), Open-Meteo data (CC BY 4.0) |
| Upstream license | MIT, © 2026 Gathin (checked on `main` today; GitHub API shows `NOASSERTION`, but the `LICENSE` file is plain MIT) |
| PR code (#39 @leovvay, #55 @yashug) | Submitted as PRs to the MIT repo; treated as MIT (inbound = outbound). Unmerged upstream at the time; credited by author |

## How this public repo was built (history preserved)

1. Cloned `Gathin23/cleanyourtv` (full history, 203 commits) and branched `develop` at `9ef40c1`,
   the exact upstream commit our code derives from.
2. One commit replaces `launcher/` with our v0.3 tree (private commit `d0d8f2d`).
3. Our 8 later commits (v0.3.1 → v0.5) re-applied with `git format-patch` / `git am`, keeping
   author and date. Resulting `launcher/` tree hash equals private tag `v0.5`
   (`e5e00c45…`), i.e. byte-identical.
4. Upstream remote kept as `upstream` with push disabled.

## Upstream merge feasibility

Upstream's `launcher/` changed +3533/−166 lines since `9ef40c1` (themes, `HomeKeyService`,
`InputKeyService`, cards, VPN badge, translations). A full merge would conflict heavily in
`MainActivity.java`. Strategy: **selective cherry-pick** of fixes, each benchmarked on the TV
(see [BRANCHING.md](BRANCHING.md)). Candidates worth reviewing first: `HomeKeyService` (Home-key
handling), the Home-chooser fix in the install scripts, `values-v28/styles.xml`.

## Reference clones (outside this repository)

Third-party trees are cloned into the working directory (`ref/`), **not** into this repository, so
they can never be committed as gitlinks or add their history here. Recorded by exact commit for
reproducible comparison:

| Repository | Commit | Date | Why |
|---|---|---|---|
| `Gathin23/tv-vibehack` | `9ef40c1de85b757cbf75dbea174a24b99a0e50a3` | 2026-10-18 | the upstream snapshot our `launcher/` derives from |
| `BrezhnevEugen/tv-vibehack` | `7b71d312d9607cff43862e19d674ce3053c8b8ab` | 2026-10-20 | fork specialised for the TCL G10; the focus/IR work |
| `sethihq/baron-tv` | `ccedda1349cc4131d2d536bed25eaf347b397f9c` | 2026-10-21 | `com.barontv.home`; ships a TCL BeyondTV debloat profile |

The same rule covers local artefacts: `private/`, `medicoes/`, `auditoria/`, `diagnostics/`,
`investigacao/` and `*.apk` are git-ignored, so pulled APKs and raw dumps stay local. Checked on
2026-10-20: no `.apk` or `.aab` is tracked in this repository.

## Rules for future code

- MIT/BSD/Apache-2.0: allowed with attribution in `NOTICE.md` (Apache-2.0 also needs its NOTICE).
- GPL/LGPL/AGPL, "non-commercial", no license: **do not copy code**; ideas only.
- Every copied snippet: add source URL, commit and license to `NOTICE.md` in the same commit.
