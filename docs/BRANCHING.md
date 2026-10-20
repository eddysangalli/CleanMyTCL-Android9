# Branching, versions and upstream

| Branch | Purpose |
|---|---|
| `main` | Stable, tested releases only. **Default branch on GitHub.** |
| `develop` | Integration (current work) |
| `feature/*` | New features (e.g. `feature/gemini-button`) |
| `perf/*` | Optimizations; must include before/after numbers in the PR |
| `fix/*` | Specific fixes |

Small changes go straight to `develop`. Tags:
- `launcher-vX.Y[.Z]` — launcher builds that were installed and measured on a device.
- `vX.Y.Z` — project releases (scripts + profiles + launcher) validated per `COMPATIBILITY.md`.

## Current repository state and promotion gates (2026-10-20)

`main` is the default branch and contains the **v0.5.8 source** (versionCode 30), including changes previously integrated through `develop`. The two branches were reconciled at commit `5922022`; subsequent review/CI/documentation commits should land in `develop` first. An ordinary commit named `release` is **not** a GitHub Release, a tag, or evidence of complete device validation.

The repository preserves CleanYourTV history from `9ef40c1`, but **`main` no longer points at that old baseline**. Do not follow the obsolete "first release" fast-forward example.

To promote a future validated version, first satisfy the release checklist below, compare the heads and review the diff. Then use an ordinary reviewed PR from `develop` to `main` (or a verified fast-forward if approved). No automatic merge or force push. Protect both branches and require successful CI before merging, where repository settings permit. Branch protection is a GitHub Settings operation and is not enabled just by adding a workflow.

```bash
git fetch origin
git log --oneline origin/main..origin/develop
git diff --stat origin/main...origin/develop
# Run the hardware checklist and review any CI results BEFORE approving a merge.
```

**What is deliberately not done:**

- No `git rebase` of our commits onto a new upstream tip — that would rewrite published history.
- No `git filter-branch` / `filter-repo` — the upstream history stays byte-identical.
- No `push --force` to `main` or `develop`, ever.
- No squashing of the upstream commits into a single "initial commit" — the ancestry is the proof
  of provenance, and [`docs/PROVENANCE.md`](PROVENANCE.md) depends on it.

**Release checklist for a `vX.Y.Z` tag** — all must be true before tagging:

- [ ] `scripts/tcl-smoke.sh` passes on the device
- [ ] `scripts/tcl-reboot-test.sh` passes (3 runs)
- [ ] `scripts/tcl-launcher-bench.sh` re-run against the version being released
- [ ] `COMPATIBILITY.md` updated so every ✅ row matches what was actually measured
- [ ] `benchmarks/results/` names the exact launcher version per number
- [ ] `tools/secret-scan.ps1` reports no secrets
- [ ] `git status` clean, working tree == tag content

## Upstream (CleanYourTV)

```bash
git remote -v                       # upstream = Gathin23/cleanyourtv (push disabled)
git fetch upstream
git log --oneline develop..upstream/main -- launcher/   # what changed
git cherry-pick -x <commit>          # on a perf/* or fix/* branch, never directly on main
```

Before merging any upstream change: review conflicts, behavior changes, and run
`scripts/tcl-launcher-bench.sh`, `tcl-coldstart.sh`, `tcl-idle-probe.sh` against the previous build.
Reject involuntary regressions in the Performance preset. An optional Visual feature may have a measured, documented and reversible GPU/RAM cost, but never crashes, unsafe recovery or broken HDMI/remote.

## Contributing back to upstream

Non-TCL-specific improvements belong upstream, not only here. The upstream `CONTRIBUTING.md` asks for
one topic per PR and a real device to test on:

1. Branch from a clean checkout of `upstream/main` (not from our `develop`).
2. Keep the diff small and in upstream's own layout (`examples/<model>/`, `launcher/`, `tools/`).
3. Include a screenshot captured on the device, and say which TV/firmware.
4. Open the PR from a fork of upstream — **not** from this repository's `main`, which carries our
   TCL-specific README and would show up as noise in the diff.

Upstream contributions may already be submitted; check their live status before referring to them as merely prepared. See [`docs/UPSTREAM-PR-DESCRIPTIONS.md`](UPSTREAM-PR-DESCRIPTIONS.md).

## Safety

No `push --force`, no `reset --hard` on shared branches, no history rewriting after publishing.
