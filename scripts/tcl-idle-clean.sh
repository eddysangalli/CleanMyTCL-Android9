#!/usr/bin/env bash
# Mede a tela parada com o sistema em repouso: fecha os apps de teste, volta para a Home,
# espera estabilizar e mede a CPU e os quadros por segundo durante SEGUNDOS.
#
# Difere do tcl-idle-probe.sh em um ponto: antes de medir, encerra os processos dos apps
# testados, para que o numero nao carregue o consumo de leftover (Netflix, YouTube, Globoplay
# e Spotify ficam vivos depois de abertos).
#
# Uso: scripts/tcl-idle-clean.sh <rotulo> [segundos=120]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-idle-clean.sh <rotulo> [segundos]}"
SECS="${2:-120}"
tv_connect

PKG=$(tv_focus_pkg)
log "Fechando apps de teste"
for p in com.netflix.ninja com.google.android.youtube.tv com.globo.globotv com.spotify.tv.android com.android.tv.settings; do
    tv am force-stop "$p" >/dev/null 2>&1
done
sleep 2
tv input keyevent KEYCODE_HOME
sleep 30
PKG=$(tv_focus_pkg)

tv dumpsys gfxinfo "$PKG" reset > /dev/null
log "Medindo ${SECS}s com o foco em $PKG"
SAMPLE=$(cpu_sample "$SECS")
FRAMES=$(tv dumpsys gfxinfo "$PKG" | awk -F': ' '/Total frames rendered/ { print $2; exit }')
FPS=$(awk -v f="${FRAMES:-0}" -v s="$SECS" 'BEGIN { printf "%.2f", f / s }')
BUSY=$(echo "$SAMPLE" | head -1)

echo "rotulo=$LABEL"
echo "foco=$PKG"
echo "segundos=$SECS"
echo "cpu_total_4nucleos_pct=$BUSY"
echo "quadros_por_segundo=$FPS"
echo "quadros_total=${FRAMES:-0}"
echo "mem_available_kb=$(tv cat /proc/meminfo | awk '/MemAvailable/ { print $2 }')"
echo "swap_used_kb=$(tv cat /proc/meminfo | awk '/SwapTotal/ { t = $2 } /SwapFree/ { f = $2 } END { print t - f }')"
echo "--- maiores consumidores (% de 1 nucleo) ---"
echo "$SAMPLE" | tail -n +2 | head -12
