#!/usr/bin/env bash
# Latencia do controle no Home, sem o resto da bateria. Serve para o A/B de uma condicao (fundo,
# mola, colunas) com varias series curtas, do jeito que o A/B do wallpaper de 19/10/2026 fez.
# Nao altera nada na TV.
#
# Latencia de uma tecla: do evento de entrada ate o 1o quadro que reage a ele (resposta) e ate o
# ultimo quadro do movimento (assentamento), pelos tempos de cada quadro em
# `dumpsys gfxinfo <pkg> framestats`. As teclas sao injetadas uma a uma com 1 s de folga, para o
# processo do `input` nao competir com o quadro.
#
# Uso: scripts/tcl-key-latency.sh <rotulo> [teclas=36] [series=3]
# Saida: medicoes/launcher/<data-hora>-<rotulo>/resumo.txt e uma linha em medicoes/launcher.csv
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-key-latency.sh <rotulo> [teclas] [series]}"
KEYS="${2:-36}"
SERIES="${3:-3}"
OUT="medicoes/launcher/$(date +%Y%m%d-%H%M%S)-$LABEL"
mkdir -p "$OUT"
tv_connect

HOME_COMPONENT=$(tv_home_component)
PKG="${HOME_COMPONENT%%/*}"
VERSION=$(tv dumpsys package "$PKG" | grep -m1 versionName | sed 's/.*=//')
log "Launcher: $HOME_COMPONENT ($VERSION) - $LABEL - $SERIES series de $KEYS teclas"

# A sequencia e a mesma da bateria completa: dentro de uma linha, para a direita e para a esquerda,
# com as bordas em pe. As teclas de borda (cima/baixo no ultimo item) tambem entram.
SEQ=(22 22 22 21 21 21 20 22 22 21 21 19)

: > "$OUT/key_latency.txt"
: > "$OUT/missed.txt"
FRAMES=0; JANKY=0

for s in $(seq 1 "$SERIES"); do
  log "Serie $s/$SERIES"
  tv input keyevent KEYCODE_HOME
  sleep 3
  # Um foco previsivel no inicio: o primeiro item da grade, para que a sequencia comece sempre do
  # mesmo lugar e o amortecimento do foco nao contamine a serie seguinte.
  tv input keyevent 19 19 19 19 19 19 19 19 19 19 19 19 19 19
  sleep 2
  for i in $(seq 0 $((KEYS - 1))); do
    key=${SEQ[$((i % ${#SEQ[@]}))]}
    # O reset zera os contadores do gfxinfo. Sem ele, o quadro da tecla anterior continuaria na
    # janela e o primeiro quadro "depois da tecla" seria, na verdade, o da tecla anterior.
    tv dumpsys gfxinfo "$PKG" reset > /dev/null
    "$ADB" -s "$SERIAL" logcat -c -b main
    tv input keyevent "$key"
    sleep 1
    tv dumpsys gfxinfo "$PKG" framestats > "$OUT/framestats_s${s}_$i.txt"
    # No Android 9 o OldestInputEvent dos quadros so e preenchido para toque, nao para teclas
    # (fica INT64_MAX). O instante da tecla vem do log do `input` (eventTime em ms de uptime, o
    # mesmo relogio dos quadros).
    t0=$("$ADB" -s "$SERIAL" logcat -d -b main | tr -d '\r' |
        grep -m1 "injectKeyEvent: KeyEvent { action=ACTION_DOWN" | sed -E 's/.*eventTime=([0-9]+).*/\1/')
    f=$(awk -F': ' '/Total frames rendered/ { print $2; exit }' "$OUT/framestats_s${s}_$i.txt")
    j=$(awk '/Janky frames/ { print $3; exit }' "$OUT/framestats_s${s}_$i.txt")
    FRAMES=$((FRAMES + ${f:-0})); JANKY=$((JANKY + ${j:-0}))
    if [ -z "$t0" ]; then
      # O log do `input` nao veio: a tecla nao foi injetada. Cuenta como perdida, nao como 0 ms.
      echo "s$s#$i sem-log" >> "$OUT/missed.txt"
      continue
    fi
    # Colunas do PROFILEDATA: 1 Flags, 2 IntendedVsync, 14 FrameCompleted (ns).
    # Flags != 0 = quadro descartado.
    # Resposta = 1o quadro iniciado depois da tecla. Movimento = fim da sequencia continua de
    # quadros (intervalo < 50 ms); quadros avulsos depois disso (relogio, saudacao) nao fazem parte
    # do movimento.
    #
    # Correcao de 20/10/2026: o filtro antigo exigia um quadro com IntendedVsync >= t0, o que
    # descartava a maioria das teclas no modo de foco instantaneo (o quadro nasce do vsync ANTERIOR
    # a tecla e so se completa depois dela). Agora o quadro e procurado pelo instante em que
    # TERMINOU: o primeiro quadro concluido depois da tecla, mesmo que ele tenha sido iniciado
    # antes. Sem esse quadro, a tecla e contada como perdida.
    awk -F, -v t0="$t0" -v key="$key" -v tag="s$s#$i" '
        /---PROFILEDATA---/ { inside = !inside; next }
        inside && $1 == "0" && $14 + 0 >= t0 * 1e6 && !done {
            if (!first) { first = $14; last = $14; prev = $2; next }
            if (($2 - prev) / 1e6 > 50) { done = 1; next }
            last = $14; prev = $2
        }
        END {
            if (first) printf "%d %d %d %s\n", (first - t0 * 1e6) / 1e6, (last - t0 * 1e6) / 1e6, key, tag
            else printf "0 0 %s %s-perdida\n", key, tag
        }' "$OUT/framestats_s${s}_$i.txt" >> "$OUT/key_latency.txt"
  done
done

# Uma tecla "perdida" (sem quadro concluido depois dela) fica fora das estatisticas: e um
# problema de medicao, nao uma latencia. Elas sao contadas e mostradas.
grep -v 'perdida$' "$OUT/key_latency.txt" > "$OUT/key_latency_ok.txt" || true
MISSED=$(grep -c 'perdida$' "$OUT/key_latency.txt" || true)
NO_LOG=$(grep -c 'sem-log' "$OUT/missed.txt" || true)

stats() {
    sort -n | awk '{ a[NR] = $1 } END {
        if (NR == 0) { print "NA NA NA NA 0"; exit }
        p50 = a[int((NR + 1) / 2)]; i95 = int(NR * 0.95 + 0.999); i99 = int(NR * 0.99 + 0.999)
        printf "%d %d %d %d %d\n", p50, a[i95 < 1 ? 1 : i95], a[i99 < 1 ? 1 : i99], a[NR], NR }'
}
read -r RESP_P50 RESP_P95 RESP_P99 RESP_MAX RESP_N < <(awk '{ print $1 }' "$OUT/key_latency_ok.txt" | stats)
read -r SET_P50 SET_P95 SET_P99 SET_MAX _ < <(awk '{ print $2 }' "$OUT/key_latency_ok.txt" | stats)
JANK_PCT=$(awk -v f="$FRAMES" -v j="$JANKY" 'BEGIN { printf "%.1f", f ? 100 * j / f : 0 }')

cat > "$OUT/resumo.txt" <<EOF
Launcher $HOME_COMPONENT ($VERSION) - $LABEL - $(date '+%Y-%m-%d %H:%M')
Series: $SERIES x $KEYS teclas
Controle   resposta (tecla -> 1o quadro): p50 ${RESP_P50} | p95 ${RESP_P95} | p99 ${RESP_P99} | max ${RESP_MAX} ms  (${RESP_N} teclas)
           movimento completo:            p50 ${SET_P50} | p95 ${SET_P95} | p99 ${SET_P99} | max ${SET_MAX} ms
           quadros atrasados: ${JANK_PCT}% de ${FRAMES}
Perdidas: ${MISSED} sem quadro apos a tecla, ${NO_LOG} sem linha do log do input
EOF
cat "$OUT/resumo.txt"
CSV=medicoes/launcher.csv
[ -f "$CSV" ] || echo "data,rotulo,versao,resp_p50,resp_p95,resp_p99,resp_max,mov_p50,mov_p95,mov_max,jank_pct,perdidas,series" > "$CSV"
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$VERSION,$RESP_P50,$RESP_P95,$RESP_P99,$RESP_MAX,$SET_P50,$SET_P95,$SET_MAX,$JANK_PCT,$MISSED,$SERIES" >> "$CSV"
log "Pronto: $OUT"
