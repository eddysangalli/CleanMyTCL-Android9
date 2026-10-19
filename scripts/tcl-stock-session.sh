#!/usr/bin/env bash
# Executa um comando com o Home temporariamente apontado para o launcher ORIGINAL da TCL
# (com.google.android.tvlauncher) e restaura o estado anterior no fim, inclusive em caso
# de erro. Nada e desinstalado: apenas enable/disable e a preferencia de Home.
#
# Uso: scripts/tcl-stock-session.sh <comando> [args...]
# Ex.: scripts/tcl-stock-session.sh ./scripts/tcl-idle-clean.sh stock-clean 120
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect

STOCK=com.google.android.tvlauncher
STOCK_COMP=com.google.android.tvlauncher/.MainActivity
EDDY_COMP=br.eddy.tvhome/com.gathin.tvhome.MainActivity
LOG=/d/DRIVE/IA/antigravity/TCL/backup/changes.log

restore() {
    tv cmd package set-home-activity --user 0 "$EDDY_COMP" >/dev/null 2>&1
    sleep 2
    tv pm disable-user --user 0 "$STOCK" >/dev/null 2>&1
    sleep 2
    now_home=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1)
    now_state=$(tv dumpsys package "$STOCK" | grep -m1 'User 0:' | grep -o 'enabled=[0-9]*')
    log "Restaurado: Home=$now_home  $STOCK $now_state"
    printf '%s\tstock-session\trestored\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$now_home" "$now_state" >> "$LOG" 2>/dev/null || true
}
trap restore EXIT

log "Habilitando o launcher original"
tv pm enable "$STOCK" >/dev/null
sleep 2
tv cmd package set-home-activity --user 0 "$STOCK_COMP" >/dev/null
sleep 3
log "Home: $(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1)"

"$@"
