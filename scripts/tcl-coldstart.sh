#!/usr/bin/env bash
# Abertura a frio do launcher que esta como Home: 5 vezes, WaitTime (relogio real) e ThisTime (desenho).
# Uso: scripts/tcl-coldstart.sh <rotulo>   -> linha em medicoes/coldstart.csv
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-coldstart.sh <rotulo>}"
tv_connect
HOME_COMPONENT=$(tv_home_component); PKG="${HOME_COMPONENT%%/*}"
W=""; T=""
for i in 1 2 3 4 5; do
    tv am start -W -n com.android.tv.settings/.MainSettings > /dev/null
    sleep 2
    tv am force-stop "$PKG"
    sleep 3
    r=$(tv am start -W -n "$HOME_COMPONENT")
    W="$W $(echo "$r" | awk -F': ' '/^WaitTime/ { print $2 }')"
    T="$T $(echo "$r" | awk -F': ' '/^ThisTime/ { print $2 }')"
    sleep 3
done
med() { echo $1 | tr ' ' '\n' | grep . | sort -n | awk '{ a[NR] = $1 } END { print a[int((NR + 1) / 2)] " (max " a[NR] ")" }'; }
VER=$(tv dumpsys package "$PKG" | grep -m1 versionName | sed 's/.*=//')
echo "$LABEL ($VER): WaitTime$W -> p50 $(med "$W") | ThisTime$T -> p50 $(med "$T")"
[ -f medicoes/coldstart.csv ] || echo "data,rotulo,versao,wait_ms,this_ms" > medicoes/coldstart.csv
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$VER,$(echo $W | tr ' ' '/'),$(echo $T | tr ' ' '/')" >> medicoes/coldstart.csv
