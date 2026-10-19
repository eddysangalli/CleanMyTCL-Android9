#!/usr/bin/env bash
# Mede a tela que estiver aberta, sem tocar em nada: CPU total, quadros por segundo do app em foco
# e maiores consumidores. Serve para comparar telas paradas (Home original, aba Apps, launcher novo...).
#
# Uso: scripts/tcl-idle-probe.sh <rotulo> [segundos=30]
# Saida: uma linha em medicoes/ocioso.csv e o detalhe na tela
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-idle-probe.sh <rotulo> [segundos]}"
SECS="${2:-30}"
tv_connect

PKG=$(tv_focus_pkg)
tv dumpsys gfxinfo "$PKG" reset > /dev/null
SAMPLE=$(cpu_sample "$SECS")
FRAMES=$(tv dumpsys gfxinfo "$PKG" | awk -F': ' '/Total frames rendered/ { print $2; exit }')
FPS=$(awk -v f="${FRAMES:-0}" -v s="$SECS" 'BEGIN { printf "%.1f", f / s }')
BUSY=$(echo "$SAMPLE" | head -1)

echo "$LABEL: foco $PKG | CPU ${BUSY}% da capacidade total | ${FPS} quadros/s"
echo "$SAMPLE" | tail -n +2 | head -6 | sed 's/^/    /'
mkdir -p medicoes
[ -f medicoes/ocioso.csv ] || echo "data,rotulo,foco,segundos,cpu_pct,fps,top1,top2,top3" > medicoes/ocioso.csv
TOP=$(echo "$SAMPLE" | tail -n +2 | head -3 | awk '{ printf "%s%s=%s", (NR > 1 ? "," : ""), $2, $1 }')
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$PKG,$SECS,$BUSY,$FPS,$TOP" >> medicoes/ocioso.csv
