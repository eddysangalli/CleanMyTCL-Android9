#!/usr/bin/env bash
# Mede o Home ORIGINAL (com.google.android.tvlauncher) nas MESMAS condicoes do Eddy TV,
# na mesma sessao, e restaura o estado anterior automaticamente.
#
# Nada e desinstalado: apenas enable/disable do launcher original e a preferencia de Home,
# que sao revertidas no fim (inclusive em caso de erro, pelo trap).
#
# Uso: scripts/tcl-bench-stock.sh <rotulo> [repeticoes]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect

LABEL="${1:?uso: tcl-bench-stock.sh <rotulo> [repeticoes]}"
N="${2:-5}"
STOCK=com.google.android.tvlauncher
STOCK_COMP=com.google.android.tvlauncher/.MainActivity
EDDY_COMP=br.eddy.tvhome/com.gathin.tvhome.MainActivity
LOG=/d/DRIVE/IA/antigravity/TCL/backup/changes.log

before_home=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1)
before_state=$(tv dumpsys package "$STOCK" | grep -m1 'User 0:' | grep -o 'enabled=[0-9]*')
log "Estado antes: Home=$before_home  $STOCK $before_state"

restore() {
    log "Restaurando o estado anterior"
    tv cmd package set-home-activity --user 0 "$EDDY_COMP" >/dev/null 2>&1
    sleep 2
    tv pm disable-user --user 0 "$STOCK" >/dev/null 2>&1
    sleep 2
    now_home=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1)
    now_state=$(tv dumpsys package "$STOCK" | grep -m1 'User 0:' | grep -o 'enabled=[0-9]*')
    log "Restaurado: Home=$now_home  $STOCK $now_state"
    printf '%s\tbench-stock\tstock-launcher-enabled\trestored\t%s\t%s\n' \
        "$(date +%Y-%m-%dT%H:%M:%S)" "$now_home" "$now_state" >> "$LOG" 2>/dev/null || true
}
trap restore EXIT

log "Habilitando o launcher original"
tv pm enable "$STOCK" >/dev/null
sleep 2
tv cmd package set-home-activity --user 0 "$STOCK_COMP" >/dev/null
sleep 3
log "Home agora: $(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1)"

./scripts/tcl-bench.sh "$LABEL" "$N"
