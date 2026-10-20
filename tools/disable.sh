#!/usr/bin/env bash
# Disable every package listed in a debloat file on the TV (one package per line, # comments allowed).
# Packages are disabled for user 0, never uninstalled. Undo with tools/restore.sh <same file>.
#
# Usage: TV_HOST=192.168.1.50 tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: disable.sh <debloat list>}"
# Resolve the list against the caller's directory, before moving into the repo.
LIST_DIR="$(cd "$(dirname "$LIST")" && pwd)"
LIST="$LIST_DIR/$(basename "$LIST")"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }

# A debloat list is a text file. If it comes from somewhere else (or a typo lands in it), a
# "package name" can carry shell metacharacters, and the line that reaches the TV is a shell
# command. Validate every name before building any command.
# See https://github.com/Gathin23/cleanyourtv/issues/165
# shellcheck source=../scripts/tcl-pkg-validate.sh
. scripts/tcl-pkg-validate.sh
if ! validate_package_list "$LIST"; then
    echo "refusing to run: the debloat list has invalid package names (see above)" >&2
    exit 1
fi
PKGS="$(filter_valid_package_list "$LIST")"

# Home guard (#239/#350): a list that disables the app owning Home can leave the TV with nothing
# useful to start. Skip that package while no other real HOME activity remains enabled. Fallback
# homes (com.android.tv.settings/.FallbackHome and vendor equivalents) do not count as a usable
# launcher. FORCE_HOME=1 overrides the guard for a TV reachable another way.
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
HOME_PKG=$("$PY" -I tools/tv.py "$HOME_QUERY" 2>/dev/null | tr -d '\r' | grep '/' | tail -n 1 | cut -d/ -f1 || true)
if [ -n "$HOME_PKG" ] && printf '%s\n' "$PKGS" | grep -qxF "$HOME_PKG" && [ -z "${FORCE_HOME:-}" ]; then
  CLAIMS=$("$PY" -I tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null \
    | tr -d '\r' | grep -oE '^[[:space:]]*[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' \
    | sed -E 's/^[[:space:]]+//' | grep -vE '/[^ ]*FallbackHome$' | cut -d/ -f1 | sort -u || true)
  # Nothing left to answer Home once the list is applied, or the TV could not say who does: either
  # way the owner is the only real Home this run knows about, so it stays enabled.
  OTHERS=$(printf '%s\n' "$CLAIMS" | grep -vxF "$HOME_PKG" | grep -vxF "$PKGS" || true)
  if [ -z "$OTHERS" ]; then
    echo "Skipping $HOME_PKG: it owns Home, and no other real app that can answer Home is installed" >&2
    echo "and staying enabled. Make the new launcher Home first, then run this list again to disable" >&2
    echo "the old one; or set FORCE_HOME=1 if you can still reach the TV." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$HOME_PKG" || true)"
  fi
fi

{
  if [ -n "$PKGS" ]; then printf '%s\n' "$PKGS" | awk '{print "pm disable-user --user 0 " $1}'; fi
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch
