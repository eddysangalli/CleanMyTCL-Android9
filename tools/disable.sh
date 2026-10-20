#!/usr/bin/env bash
# Disable every package listed in a debloat file on the TV (one package per line, # comments allowed).
# Packages are disabled for user 0, never uninstalled. Undo with tools/restore.sh <same file>.
#
# Usage: TV_HOST=192.168.1.50 tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: disable.sh <debloat list>}"
# Resolve the list against the caller's directory, before moving into the repo.
# Git Bash's pwd says /c/Users/..., which the Windows Python cannot open with
# MSYS_NO_PATHCONV=1 set; pwd -W gives C:/Users/..., the same path (and so the same record) as
# the PowerShell scripts.
LIST_DIR="$(cd "$(dirname "$LIST")" && { pwd -W 2>/dev/null || pwd; })"
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

# A package that asks for android.permission.TV_INPUT_HARDWARE is the software layer under the HDMI, AV
# and tuner inputs: routing, the HDCP handshake and scaling live in it. Disable one and the port still
# notices the cable but shows "No Signal" for good; re-enabling the pieces in batches does not bring the
# picture back, a full restore and a cold boot do (#321, OnePlus TV on Android 11). Keep those packages;
# TVINPUT_ALLOW=1 disables them anyway.
if [ -n "$PKGS" ] && [ -z "${TVINPUT_ALLOW:-}" ]; then
  # One round trip: the loop runs on the TV, where `dumpsys package` prints the permissions a package
  # asks for. Every name was checked as a bare package name above, so none of them can add shell syntax
  # here. </dev/null so the client cannot read this script's stdin (that is the piped batch below);
  # tr -d '\r' because adb can end lines with CRLF, which would leave a CR on every name.
  NAMES="$(printf '%s ' $PKGS)"
  # Trailing `; true`: the loop's status is the last package's `grep -q`, which is nonzero whenever
  # the last listed package is not an input driver — the common case — and would look like a failed
  # check. Only a real failure (tv.py cannot reach the TV) should reach the warning below.
  if TV_HW="$("$PY" -I tools/tv.py \
      "for p in $NAMES; do dumpsys package \$p | grep -q TV_INPUT_HARDWARE && echo \$p; done; true" \
      </dev/null 2>/dev/null | tr -d '\r')"; then
    for p in $TV_HW; do
      # grep without -q: with pipefail an early exit could SIGPIPE printf and fail the run (see above).
      printf '%s\n' "$PKGS" | grep -xF "$p" >/dev/null || continue
      echo "Skipping $p: it asks for android.permission.TV_INPUT_HARDWARE, so it is the software layer" >&2
      echo "under the HDMI/AV/tuner inputs. With it disabled those inputs still notice the cable but stay" >&2
      echo "black (\"No Signal\"), and re-enabling the pieces in batches does not restore the picture: a" >&2
      echo "full restore plus a cold boot does (#321). Set TVINPUT_ALLOW=1 to disable it." >&2
      PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$p" || true)"
    done
  else
    # Do not skip anything on a guess, but say the check did not run: a black input is easy to blame on
    # the cable, and the ports are the one thing the owner cannot test from here.
    echo "Could not check which listed packages drive the HDMI/AV inputs (tv.py failed). Check every" >&2
    echo "input after this run: a port that notices the cable and stays black means one of them was" >&2
    echo "disabled, and only a full restore and a cold boot bring it back (#321)." >&2
  fi
fi

# Network ADB warning (#319): on firmware with ro.adb.secure=0, or with the property not set at all,
# port 5555 hands a shell to any device on the network without the "Allow" prompt. Read it and say
# so once, before the first command reaches the TV, so the note is on screen even if the run dies
# halfway. A read that fails is not a guess: it only skips the note.
ADB_SECURE=""
ADB_SECURE_READ=0
if ADB_SECURE_RAW=$("$PY" -I tools/tv.py "getprop ro.adb.secure" 2>/dev/null | tr -d '[:space:]'); then
  ADB_SECURE="$ADB_SECURE_RAW"
  ADB_SECURE_READ=1
fi
if [ "$ADB_SECURE_READ" = "1" ] && { [ "$ADB_SECURE" = "0" ] || [ -z "$ADB_SECURE" ]; }; then
  echo "Warning: this TV reports ro.adb.secure=${ADB_SECURE:-<empty>}, so network ADB on port 5555 accepts" >&2
  echo "any device on your network without the \"Allow\" prompt. When you are done, turn USB debugging" >&2
  echo "and Network/ADB debugging off on the TV, reboot it, and check that the port is gone." >&2
fi

# Record the state the list is about to change, before the first command reaches the TV (#317,
# from upstream). Without it, restore.sh would enable every listed package even ones that were
# disabled before cleanup, and would reset Home and animation scales to fixed values instead of
# what they were. A retry after a half-applied list must not overwrite the first record, so this
# runs before the disable batch: if it fails (e.g. tv.py cannot reach the TV), no package is
# disabled and nothing reaches the device.
# 2>&1 so the record is visible alongside the disable output. The script's own exit stops the run.
"$PY" -I tools/recovery.py --save "$LIST"

{
  if [ -n "$PKGS" ]; then printf '%s\n' "$PKGS" | awk '{print "pm disable-user --user 0 " $1}'; fi
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch
