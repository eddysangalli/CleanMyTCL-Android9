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

## Current state of `main` and how a release is published

`main` holds the released launcher builds and `develop` continues from it, so `main` is always an
ancestor of `develop` and a release is a **fast-forward**, not a rewrite:

```bash
git merge-base --is-ancestor main develop && echo "fast-forward is possible"
```

That makes a release:

```bash
# 1. work on develop, never directly on main
git checkout develop
# ... run the full validation ...

# 2. fast-forward main onto it (no merge commit, no history loss, no force push)
git checkout main
git merge --ff-only develop
git push origin main
git push origin --tags
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

### Where the v0.5.8 stands against this checklist

| Item | State |
|---|---|
| Smoke test | **Done for v0.5.8**: 24/24 OK on 2026-10-20 17:08, and again after each of the 4 reboots |
| 3 reboots | **Done for v0.5.8**: 4 runs on 2026-10-20 (17:11, 17:18, 17:19, 17:22), 39–42 s to `boot_completed`, disabled packages kept, Home unchanged, smoke OK each time, accessibility service survived |
| Launcher benchmark re-run on the released build | **Partly.** The spring A/B and the `tools/bench.py` run are on the v0.5.8 build; the full 5-repetition battery is pending |
| `COMPATIBILITY.md` | Updated to the v0.5.8 build that is on the device |
| `benchmarks/results/` names the version | Yes, each report names the version it measured |
| Secret scan | `tools/secret-scan.ps1` — 0 findings |
| Clean tree | Yes |

**So the v0.5.8 is published as a launcher build (tag `launcher-v0.5.8`) and as a project release
(tag `v0.5.8`), with the full 5-repetition battery still to come.** The battery measures the
already-released build, so it refines the numbers rather than gating the release; when it is done
it goes into `benchmarks/results/` and the matrix, with the version it measured.

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

Two PRs are prepared — see [`docs/UPSTREAM-PR-DESCRIPTIONS.md`](UPSTREAM-PR-DESCRIPTIONS.md).

Three have been sent and are on the upstream board:

| PR | State |
|---|---|
| [#435](https://github.com/Gathin23/cleanyourtv/pull/435) — a vendor `FallbackHome` is not a second launcher in the Home guard | **Merged** by the maintainer |
| [#436](https://github.com/Gathin23/cleanyourtv/pull/436) — refit the glass highlight when cold-start layout settles | **Merged** by the maintainer |
| [#437](https://github.com/Gathin23/cleanyourtv/pull/437) — a benchmark for the launcher's key latency, jank and idle cost | **Open**, with the reviewer's three points addressed |

## Safety

No `push --force`, no `reset --hard` on shared branches, no history rewriting after publishing.
