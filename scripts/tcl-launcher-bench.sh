#!/usr/bin/env bash
# Benchmark do launcher que esta como Home: latencia do controle (tipica e piores casos), abertura a frio,
# volta ao Home, CPU e quadros com a tela parada e memoria. Nao altera nada na TV.
#
# Latencia de uma tecla: do evento de entrada ate o 1o quadro que reage a ele (resposta) e ate o ultimo
# quadro do movimento (assentamento), pelos tempos de cada quadro em `dumpsys gfxinfo <pkg> framestats`.
# As teclas sao injetadas uma a uma com 1 s de folga, para o processo do `input` nao competir com o quadro.
#
# Uso: scripts/tcl-launcher-bench.sh <rotulo> [teclas=36] [voltas=10]
# Saida: medicoes/launcher/<data-hora>-<rotulo>/resumo.txt e uma linha em medicoes/launcher.csv
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-launcher-bench.sh <rotulo> [teclas] [voltas]}"
KEYS="${2:-36}"
RETURNS="${3:-10}"
OUT="medicoes/launcher/$(date +%Y%m%d-%H%M%S)-$LABEL"
mkdir -p "$OUT"
tv_connect

HOME_COMPONENT=$(tv_home_component)
PKG="${HOME_COMPONENT%%/*}"
CLASS="$(component_class "$HOME_COMPONENT")"
VERSION=$(tv dumpsys package "$PKG" | grep -m1 versionName | sed 's/.*=//')
log "Launcher: $HOME_COMPONENT ($VERSION)"

# p50/p95/p99/max de uma lista de numeros (um por linha)
stats() {
    sort -n | awk '{ a[NR] = $1 } END {
        if (NR == 0) { print "NA NA NA NA 0"; exit }
        p50 = a[int((NR + 1) / 2)]; i95 = int(NR * 0.95 + 0.999); i99 = int(NR * 0.99 + 0.999)
        printf "%d %d %d %d %d\n", p50, a[i95 < 1 ? 1 : i95], a[i99 < 1 ? 1 : i99], a[NR], NR }'
}

# ---- abertura a frio do launcher -----------------------------------------------------------------
log "Abertura a frio (5x)"
: > "$OUT/cold_ms.txt"
# O force-stop de cada volta apaga a lista de servicos de acessibilidade nesta TCL: guarda agora e
# devolve no fim do script, para a medicao nao deixar o controle sem remapeamento.
tv_acc_save
trap tv_acc_restore EXIT
for i in 1 2 3 4 5; do
    tv am start -W -n com.android.tv.settings/.MainSettings > /dev/null
    sleep 2
    tv am force-stop "$PKG"
    sleep 3
    # WaitTime = do comando ate a tela desenhada (relogio real). O TotalTime pode incluir a abertura anterior
    # (as Configuracoes) quando o Android junta as duas na mesma sequencia, e dava ~3,7 s falsos.
    tv am start -W -n "$HOME_COMPONENT" | tee -a "$OUT/cold_raw.txt" | awk -F': ' '/^WaitTime/ { print $2 }' >> "$OUT/cold_ms.txt"
    sleep 3
done

# ---- volta ao Home: tecla Home -> launcher retomado ----------------------------------------------
log "Volta ao Home (${RETURNS}x)"
: > "$OUT/return_ms.txt"
for i in $(seq 1 "$RETURNS"); do
    tv am start -W -n com.android.tv.settings/.MainSettings > /dev/null
    sleep 3
    "$ADB" -s "$SERIAL" logcat -c -b main -b events
    tv input keyevent KEYCODE_HOME
    sleep 3
    "$ADB" -s "$SERIAL" logcat -d -b main -b events -v epoch | tr -d '\r' |
        awk -v cls="$CLASS" '
            /injectKeyEvent: KeyEvent \{ action=ACTION_DOWN, keyCode=KEYCODE_HOME/ && !k { k = $1 }
            k && /am_on_resume_called/ && index($0, cls) && !r { r = $1 }
            END { if (k && r) printf "%d\n", (r - k) * 1000 }' >> "$OUT/return_ms.txt"
done
PSS_AFTER=$(tv dumpsys meminfo "$PKG" | awk '/TOTAL:/ { print int($2 / 1024); exit }')

# ---- latencia do controle no Home -----------------------------------------------------------------
log "Latencia do controle ($KEYS teclas)"
tv input keyevent KEYCODE_HOME
sleep 3
tv input keyevent 21 21 21 21    # inicio do dock
sleep 2
SEQ=(22 22 22 21 21 21 20 22 22 21 21 19)
: > "$OUT/key_latency.txt"
FRAMES=0; JANKY=0
for i in $(seq 0 $((KEYS - 1))); do
    key=${SEQ[$((i % ${#SEQ[@]}))]}
    # O Uptime do reset e do dump delimita a janela em que a tecla aconteceu: uma linha de log
    # de outra tecla cai fora dela e seria contada como segundos de atraso.
    reset_out=$(tv dumpsys gfxinfo "$PKG" reset)
    t_before=$(echo "$reset_out" | awk '/^Uptime:/ { print $2; exit }')
    "$ADB" -s "$SERIAL" logcat -c -b main
    tv input keyevent "$key"
    sleep 1
    tv dumpsys gfxinfo "$PKG" framestats > "$OUT/framestats_$i.txt"
    t_after=$(awk '/^Uptime:/ { print $2; exit }' "$OUT/framestats_$i.txt")
    # No Android 9 o OldestInputEvent dos quadros so e preenchido para toque, nao para teclas (fica INT64_MAX).
    # O instante da tecla vem do log do `input` (eventTime em ms de uptime, o mesmo relogio dos quadros).
    t0=$("$ADB" -s "$SERIAL" logcat -d -b main | tr -d '\r' |
        grep -m1 "injectKeyEvent: KeyEvent { action=ACTION_DOWN" | sed -E 's/.*eventTime=([0-9]+).*/\1/')
    f=$(awk -F': ' '/Total frames rendered/ { print $2; exit }' "$OUT/framestats_$i.txt")
    j=$(awk '/Janky frames/ { print $3; exit }' "$OUT/framestats_$i.txt")
    FRAMES=$((FRAMES + ${f:-0})); JANKY=$((JANKY + ${j:-0}))
    [ -n "$t0" ] || continue
    # Resposta = 1o quadro que TERMINOU depois da tecla; movimento = fim da sequencia continua.
    # A regra fica em scripts/frame-match.awk, a mesma de tools/bench.py, e tem teste proprio.
    awk -v t0="$t0" -v t_before="$t_before" -v t_after="$t_after" -v key="$key" \
        -f scripts/frame-match.awk "$OUT/framestats_$i.txt" >> "$OUT/key_latency.txt"
done
read -r RESP_P50 RESP_P95 RESP_P99 RESP_MAX RESP_N < <(awk '{ print $1 }' "$OUT/key_latency.txt" | stats)
read -r SET_P50 SET_P95 SET_P99 SET_MAX _ < <(awk '{ print $2 }' "$OUT/key_latency.txt" | stats)
JANK_PCT=$(awk -v f="$FRAMES" -v j="$JANKY" 'BEGIN { printf "%.1f", f ? 100 * j / f : 0 }')

# ---- tela parada ---------------------------------------------------------------------------------
log "Tela parada (60 s)"
tv input keyevent KEYCODE_HOME
sleep 20
tv dumpsys gfxinfo "$PKG" reset > /dev/null
cpu_sample 60 > "$OUT/cpu.txt"
CPU=$(head -1 "$OUT/cpu.txt")
FPS=$(tv dumpsys gfxinfo "$PKG" | awk -F': ' '/Total frames rendered/ { printf "%.1f", $2 / 60; exit }')
# O kernel guarda os ultimos 15 caracteres do nome do processo (com.google.android.tvlauncher -> roid.tvlauncher).
LAUNCHER_CPU=$(awk -v p="$PKG" 'NR > 1 && substr(p, length(p) - length($2) + 1) == $2 { print $1; exit }' "$OUT/cpu.txt")
PSS=$(tv dumpsys meminfo "$PKG" | awk '/TOTAL:/ { print int($2 / 1024); exit }')

read -r COLD_P50 _ _ COLD_MAX _ < <(stats < "$OUT/cold_ms.txt")
read -r RET_P50 RET_P95 _ RET_MAX _ < <(stats < "$OUT/return_ms.txt")

cat > "$OUT/resumo.txt" <<EOF
Launcher $HOME_COMPONENT ($VERSION) - $LABEL - $(date '+%Y-%m-%d %H:%M')
Controle   resposta (tecla -> 1o quadro): p50 ${RESP_P50} | p95 ${RESP_P95} | p99 ${RESP_P99} | max ${RESP_MAX} ms  (${RESP_N} teclas)
           movimento completo:            p50 ${SET_P50} | p95 ${SET_P95} | p99 ${SET_P99} | max ${SET_MAX} ms
           quadros atrasados: ${JANK_PCT}% de ${FRAMES}
Abertura   a frio: p50 ${COLD_P50} | max ${COLD_MAX} ms
Volta Home p50 ${RET_P50} | p95 ${RET_P95} | max ${RET_MAX} ms
Parada     CPU total ${CPU}% | launcher ${LAUNCHER_CPU:-0}% de 1 nucleo | ${FPS} quadros/s
Memoria    PSS ${PSS} MB (apos ${RETURNS} voltas: ${PSS_AFTER} MB)
EOF
cat "$OUT/resumo.txt"
CSV=medicoes/launcher.csv
[ -f "$CSV" ] || echo "data,rotulo,versao,resp_p50,resp_p95,resp_p99,resp_max,mov_p50,mov_p95,mov_max,jank_pct,frio_p50,frio_max,volta_p50,volta_p95,volta_max,cpu_pct,launcher_cpu,fps,pss_mb,pss_voltas_mb" > "$CSV"
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$VERSION,$RESP_P50,$RESP_P95,$RESP_P99,$RESP_MAX,$SET_P50,$SET_P95,$SET_MAX,$JANK_PCT,$COLD_P50,$COLD_MAX,$RET_P50,$RET_P95,$RET_MAX,$CPU,${LAUNCHER_CPU:-0},$FPS,$PSS,$PSS_AFTER" >> "$CSV"
log "Pronto: $OUT"
