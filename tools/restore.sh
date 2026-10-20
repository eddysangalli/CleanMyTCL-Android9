#!/usr/bin/env bash
# Re-enable every package listed in a debloat file, restore Home, and reset animation scales.
# Pass the same list you used with tools/disable.sh.
#
# Usage: TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt [stock.launcher.package]
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: restore.sh <debloat list> [stock launcher package]}"
# Resolve the list against the caller's directory, before moving into the repo.
# Git Bash's pwd says /c/Users/..., which the Windows Python cannot open with
# MSYS_NO_PATHCONV=1 set; pwd -W gives C:/Users/..., the same path (and so the same record) as
# the PowerShell scripts.
LIST_DIR="$(cd "$(dirname "$LIST")" && { pwd -W 2>/dev/null || pwd; })"
LIST="$LIST_DIR/$(basename "$LIST")"
STOCK="${2:-com.google.android.tvlauncher}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1

# Validate the list file before invoking recovery.py, so a typo in the list fails the same way it
# would have failed in restore.sh's old inline batch. The package-name regex is the one shared
# across the tools.
# See https://github.com/Gathin23/cleanyourtv/issues/165
# shellcheck source=../scripts/tcl-pkg-validate.sh
. scripts/tcl-pkg-validate.sh
if ! validate_package_list "$LIST"; then
    echo "refusing to run: the debloat list has invalid package names (see above)" >&2
    exit 1
fi
if ! validate_package_name "$STOCK"; then
    echo "refusing to run: the stock launcher package name is invalid" >&2
    exit 1
fi

# Replay the state recorded by disable.sh (or warn and fall back to "enable every listed
# package + reset everything" when there is no record). recovery.py also validates the record
# before anything reaches the TV: a hand-edited or damaged record fails here (#317, from
# upstream), not halfway through.
"$PY" -I tools/recovery.py --commands "$LIST" "$STOCK" | "$PY" -I tools/tv.py --batch
