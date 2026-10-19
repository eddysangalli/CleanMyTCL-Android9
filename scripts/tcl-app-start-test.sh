#!/usr/bin/env bash
# Abre um app de streaming e registra a linha do tempo ate ele ficar utilizavel, sem mexer em dados do app:
#  - 1a tela: am start -W (WaitTime = do comando ate o 1o quadro)
#  - a cada 2 s: CPU total da TV e CPU do processo do app (% de 1 nucleo)
#  - a cada 5 s: uma captura de tela (para marcar quando o catalogo aparece; custa ~1 s de 1 nucleo)
# Nao limpa cache, nao troca perfil, nao mexe em conta.
#
# Uso: scripts/tcl-app-start-test.sh <netflix|youtube|globoplay> <rotulo> [segundos=120] [frio|quente]
#      frio = encerra o processo do app antes (padrao); quente = so traz de volta
# Saida: investigacao/netflix-boot/<data-hora>-<app>-<rotulo>/ (timeline.csv, shots/, am_start.txt)
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
APP="${1:?uso: tcl-app-start-test.sh <netflix|youtube|globoplay> <rotulo> [segundos] [frio|quente]}"
LABEL="${2:?rotulo}"
SECS="${3:-120}"
MODE="${4:-frio}"
case "$APP" in
    netflix)   PKG=com.netflix.ninja; COMP=com.netflix.ninja/.MainActivity ;;
    youtube)   PKG=com.google.android.youtube.tv; COMP=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity ;;
    globoplay) PKG=com.globo.globotv; COMP=com.globo.globotv/.splashtv.SplashActivity ;;
    *) echo "app desconhecido: $APP"; exit 2 ;;
esac
OUT="investigacao/netflix-boot/$(date +%Y%m%d-%H%M%S)-$APP-$LABEL"
mkdir -p "$OUT/shots"
tv_connect

UPTIME=$(tv cat /proc/uptime | cut -d' ' -f1)
log "$APP ($MODE), uptime da TV ${UPTIME}s, $SECS s de registro -> $OUT"
if [ "$MODE" = frio ]; then
    tv am force-stop "$PKG"
    sleep 2
fi
"$ADB" -s "$SERIAL" logcat -c -b main -b system -b events

T0=$(date +%s.%N)
# Mesmo intent que o launcher usa ao abrir um app pelo bloco
tv am start -W -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -f 0x10200000 -n "$COMP" > "$OUT/am_start.txt"
T1=$(date +%s.%N)
WAIT=$(awk -F': ' '/^WaitTime/ { print $2 }' "$OUT/am_start.txt")
PID=$(tv pidof "$PKG")
log "1a tela: WaitTime ${WAIT} ms (pid $PID)"

echo "t_s,cpu_total_pct,app_pct_1nucleo" > "$OUT/timeline.csv"
prev=$(tv "head -1 /proc/stat; cat /proc/$PID/stat 2>/dev/null")
n=0
while :; do
    now=$(date +%s.%N)
    el=$(awk -v a="$now" -v b="$T0" 'BEGIN { printf "%.1f", a - b }')
    awk -v e="$el" -v s="$SECS" 'BEGIN { exit !(e >= s) }' && break
    if [ $((n % 5)) -eq 0 ]; then
        "$ADB" -s "$SERIAL" exec-out screencap -p > "$OUT/shots/$(printf '%05.1f' "$el").png" 2>/dev/null
    fi
    sleep 2
    cur=$(tv "head -1 /proc/stat; cat /proc/$PID/stat 2>/dev/null")
    el2=$(awk -v a="$(date +%s.%N)" -v b="$T0" 'BEGIN { printf "%.1f", a - b }')
    printf '%s\n===\n%s\n' "$prev" "$cur" | awk -v t="$el2" '
        /^===/ { ph = 2; next }
        /^cpu / { tot = 0; for (i = 2; i <= NF; i++) tot += $i; idle = $5 + $6
                  if (ph == 2) { T2 = tot; I2 = idle } else { T1 = tot; I1 = idle }; next }
        { c = 0; for (i = length($0); i > 0; i--) if (substr($0, i, 1) == ")") { c = i; break }
          if (!c) next; split(substr($0, c + 2), f, " ")
          if (ph == 2) A2 = f[12] + f[13]; else A1 = f[12] + f[13] }
        END { dt = (T2 - T1) / 4   # ticks por nucleo no intervalo (4 nucleos)
              printf "%s,%.1f,%.1f\n", t, 100 * (1 - (I2 - I1) / (T2 - T1)), dt > 0 ? 100 * (A2 - A1) / dt : 0 }' >> "$OUT/timeline.csv"
    prev="$cur"
    n=$((n + 1))
done
"$ADB" -s "$SERIAL" logcat -d -b main -b system -b events -v epoch | tr -d '\r' > "$OUT/logcat.txt"
echo "$T0" > "$OUT/t0_epoch.txt"
echo "app=$APP modo=$MODE uptime_inicio=${UPTIME}s waittime_ms=$WAIT pid=$PID" > "$OUT/resumo.txt"
cat "$OUT/resumo.txt"
log "Pronto: $OUT ($(ls "$OUT/shots" | wc -l) capturas)"
