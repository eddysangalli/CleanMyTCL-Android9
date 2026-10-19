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

## Current state of `main` and how the first stable release is published

`main` currently points at the upstream baseline commit `9ef40c1` (the CleanYourTV / tv-vibehack
README). `develop` continues from it — **all of our work is on top of the upstream history, not on a
squashed or re-based copy.** `main` is therefore a direct ancestor of `develop`:

```bash
git merge-base --is-ancestor main develop && echo "fast-forward is possible"
```

That makes the first release a **fast-forward**, not a rewrite:

```bash
# 1. work on a release branch, never directly on main
git checkout -b release/v0.5.0 develop
# ... run the full validation ...

# 2. fast-forward main onto it (no merge commit, no history loss, no force push)
git checkout main
git merge --ff-only release/v0.5.0
git push origin main
git push origin --tags

# 3. point GitHub's default branch at main
gh repo edit --default-branch main
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
Reject it if p50 or tail latency, idle CPU or PSS get worse.

## Contributing back to upstream

Non-TCL-specific improvements belong upstream, not only here. The upstream `CONTRIBUTING.md` asks for
one topic per PR and a real device to test on:

1. Branch from a clean checkout of `upstream/main` (not from our `develop`).
2. Keep the diff small and in upstream's own layout (`examples/<model>/`, `launcher/`, `tools/`).
3. Include a screenshot captured on the device, and say which TV/firmware.
4. Open the PR from a fork of upstream — **not** from this repository's `main`, which carries our
   TCL-specific README and would show up as noise in the diff.

Two PRs are prepared (locally, not submitted) — see [`docs/UPSTREAM-CONTRIBUTIONS.md`](UPSTREAM-CONTRIBUTIONS.md).

## Safety

No `push --force`, no `reset --hard` on shared branches, no history rewriting after publishing.
