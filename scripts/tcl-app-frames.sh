#!/usr/bin/env bash
# Assina o app pela taxa de quadros: a cada N segundos le o total de quadros desenhados
# pelo processo. Serve para apps que desenham tudo numa surface propria (Netflix), onde
# nem o screencap (bloqueado por DRM) nem a arvore de acessibilidade mostram o conteudo.
#
# Uso: scripts/tcl-app-frames.sh <app> <rotulo> [segundos=180] [frio|quente]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh

APP="${1:?uso: tcl-app-frames.sh <app> <rotulo> [seg] [frio|quente]}"
LABEL="${2:?rotulo}"
SECS="${3:-180}"
MODE="${4:-frio}"
case "$APP" in
    netflix)   PKG=com.netflix.ninja; COMP=com.netflix.ninja/.MainActivity ;;
    youtube)   PKG=com.google.android.youtube.tv; COMP=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity ;;
    globoplay) PKG=com.globo.globotv; COMP=com.globo.globotv/.splashtv.SplashActivity ;;
    spotify)   PKG=com.spotify.tv.android; COMP=com.spotify.tv.android/.SpotifyTVActivity ;;
    *) echo "app desconhecido: $APP"; exit 2 ;;
esac
OUT="medicoes/app-frames/$(date +%Y%m%d-%H%M%S)-$APP-$LABEL"
mkdir -p "$OUT"
tv_connect

tv input keyevent KEYCODE_WAKEUP
sleep 3
if [ "$MODE" = frio ]; then tv am force-stop "$PKG"; sleep 2; fi

T0=$(date +%s.%N)
tv am start -W -n "$COMP" > "$OUT/am_start.txt" 2>/dev/null
WAIT=$(awk -F': ' '/^WaitTime/ { print $2 }' "$OUT/am_start.txt")
log "$APP aberto, WaitTime ${WAIT} ms"

echo "t_s,quadros_total,fps_intervalo,pid_vivo" > "$OUT/frames.csv"
prev=0
prevt=0
while :; do
    el=$(awk -v a="$(date +%s.%N)" -v b="$T0" 'BEGIN { printf "%.1f", a - b }')
    awk -v e="$el" -v s="$SECS" 'BEGIN { exit !(e >= s) }' && break
    F=$(tv dumpsys gfxinfo "$PKG" | awk -F': ' '/Total frames rendered/ { print $2; exit }')
    F=${F:-0}
    PID=$(tv pidof "$PKG")
    if [ "$prev" -gt 0 ] && [ "$F" -gt "$prev" ]; then
        FPS=$(awk -v a="$F" -v b="$prev" -v d="$(awk -v x="$el" -v y="$prevt" 'BEGIN { printf "%.1f", x - y }')" 'BEGIN { printf "%.1f", (d > 0) ? (a - b) / d : 0 }')
    else
        FPS=0.0
    fi
    printf '%s,%s,%s,%s\n' "$el" "$F" "$FPS" "${PID:-nao}" >> "$OUT/frames.csv"
    prev=$F; prevt=$el
    sleep 3
done
cat "$OUT/frames.csv"
