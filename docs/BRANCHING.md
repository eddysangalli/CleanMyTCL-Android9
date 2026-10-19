# Branching, versions and upstream

| Branch | Purpose |
|---|---|
| `main` | Stable, tested releases only (created at the first release) |
| `develop` | Integration (current work) |
| `feature/*` | New features (e.g. `feature/gemini-button`) |
| `perf/*` | Optimizations; must include before/after numbers in the PR |
| `fix/*` | Specific fixes |

Small changes go straight to `develop`. Tags:
- `launcher-vX.Y[.Z]` — launcher builds that were installed and measured on a device.
- `vX.Y.Z` — project releases (scripts + profiles + launcher) validated per `COMPATIBILITY.md`.

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

## Safety

No `push --force`, no `reset --hard` on shared branches, no history rewriting after publishing.
