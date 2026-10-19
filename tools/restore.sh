#!/usr/bin/env bash
# Re-enable every package listed in a debloat file, restore the stock launcher as home and reset
# animation scales. Pass the same list you used with tools/disable.sh.
#
# Usage: TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt [stock.launcher.package]
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: restore.sh <debloat list> [stock launcher package]}"
# Resolve the list against the caller's directory, before moving into the repo.
LIST_DIR="$(cd "$(dirname "$LIST")" && pwd)"
LIST="$LIST_DIR/$(basename "$LIST")"
STOCK="${2:-com.google.android.tvlauncher}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1

# Same validation as tools/disable.sh: nothing from a list file reaches the TV shell unvalidated.
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

{
  filter_valid_package_list "$LIST" | awk '{print "pm enable " $1}'
  echo "pm enable $STOCK"
  # resolve the stock HOME component on the TV (Android 9 needs pkg/activity, not just pkg)
  echo "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK | tail -n 1)"
  echo "settings put global window_animation_scale 1.0"
  echo "settings put global transition_animation_scale 1.0"
  echo "settings put global animator_duration_scale 1.0"
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch
