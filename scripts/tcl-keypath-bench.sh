#!/usr/bin/env bash
# Atraso de entrega das teclas pelo caminho REAL do controle: as setas sao escritas no dispositivo de
# entrada do receptor IR (/dev/input/eventN "mtkinp") com sendevent, entao passam pelo leitor de entrada,
# pelo filtro de acessibilidade (se houver um servico ligado) e pelo despachante, como uma tecla de verdade.
# (O `input keyevent` injeta depois do filtro e nao mostraria o custo de um remapeador.)
#
# O launcher registra, para cada tecla, quanto tempo ela levou do kernel ate chegar nele
# (requer settings global eddy_debug_keys=1, ligado e desligado por este script).
#
# Uso: scripts/tcl-keypath-bench.sh <rotulo> [teclas=40]
# Saida: medicoes/keypath.csv (p50, p95, p99, max) e medicoes/keypath/<data-hora>-<rotulo>.txt
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-keypath-bench.sh <rotulo> [teclas]}"
N="${2:-40}"
mkdir -p medicoes/keypath
OUT="medicoes/keypath/$(date +%Y%m%d-%H%M%S)-$LABEL.txt"
tv_connect

DEV=$(tv getevent -lp 2>/dev/null | awk '/^add device/ { d = $4 } /name:.*"mtkinp"/ { print d; exit }')
[ -n "$DEV" ] || { echo "receptor mtkinp nao encontrado"; exit 2; }
ACC=$(tv settings get secure enabled_accessibility_services)
log "Dispositivo $DEV | acessibilidade: ${ACC:-nenhuma} | $N teclas"

tv settings put global eddy_debug_keys 1
tv input keyevent KEYCODE_HOME      # retoma o launcher, que le o ajuste de depuracao no onResume
sleep 3
"$ADB" -s "$SERIAL" logcat -c -b main
for i in $(seq 1 "$N"); do
    code=$([ $((i % 2)) -eq 1 ] && echo 106 || echo 105)   # direita / esquerda
    tv "sendevent $DEV 1 $code 1; sendevent $DEV 0 0 0; sendevent $DEV 1 $code 0; sendevent $DEV 0 0 0"
    sleep 0.6
done
sleep 1
"$ADB" -s "$SERIAL" logcat -d -b main -s EddyKey | tr -d '\r' | grep -oE "delay=[0-9]+" | cut -d= -f2 > "$OUT"
tv settings delete global eddy_debug_keys > /dev/null

read -r P50 P95 P99 MAX COUNT < <(sort -n "$OUT" | awk '{ a[NR] = $1 } END {
    if (NR == 0) { print "NA NA NA NA 0"; exit }
    i95 = int(NR * 0.95 + 0.999); i99 = int(NR * 0.99 + 0.999)
    print a[int((NR + 1) / 2)], a[i95], a[i99], a[NR], NR }')
echo "$LABEL: entrega da tecla p50 ${P50} | p95 ${P95} | p99 ${P99} | max ${MAX} ms ($COUNT de $N teclas)"
[ -f medicoes/keypath.csv ] || echo "data,rotulo,acessibilidade,p50_ms,p95_ms,p99_ms,max_ms,teclas" > medicoes/keypath.csv
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,${ACC:-nenhuma},$P50,$P95,$P99,$MAX,$COUNT" >> medicoes/keypath.csv
