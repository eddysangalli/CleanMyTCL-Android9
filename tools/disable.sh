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

{
  filter_valid_package_list "$LIST" | awk '{print "pm disable-user --user 0 " $1}'
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch
