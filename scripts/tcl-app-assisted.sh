#!/usr/bin/env bash
# Teste ASSISTIDO: mede quanto tempo o app leva ate ficar PRONTO DE VER.
#
# Por que precisa de uma pessoa: o Netflix desenha o catalogo inteiro numa surface propria
# (SurfaceView). Consequencias medidas nesta TV:
#   - 'screencap' devolve vazio (janela com FLAG_SECURE por causa do DRM): a imagem nao sai;
#   - 'uiautomator dump' expoe a hierarquia, mas so com os containers (gibbonMain/gibbon),
#     sem as linhas de titulo: nao da para saber o que esta na tela;
#   - 'dumpsys gfxinfo' conta 2 quadros no total, porque a renderizacao do catalogo nao
#     passa pela view da activity.
# Nenhum desses metodos automatizaveis ve o catalogo. Logo, a confirmacao visual e humana.
#
# Como funciona: o script abre o app, marca T0 e fica esperando a primeira tecla do controle
# remoto (lida direto do dispositivo de entrada, como o script de keypath). Voce pressiona
# QUALQUER tecla do remote no instante em que o catalogo aparecer na TV; o script registra
# quanto tempo passou.
#
# Uso: scripts/tcl-app-assisted.sh <app> <rotulo> [frio|quente]
#   depois de abrir, pressione uma tecla no controle quando o conteudo aparecer
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh

APP="${1:?uso: tcl-app-assisted.sh <netflix|youtube|globoplay|spotify> <rotulo> [frio|quente]}"
LABEL="${2:?rotulo}"
MODE="${3:-frio}"
case "$APP" in
    netflix)   PKG=com.netflix.ninja; COMP=com.netflix.ninja/.MainActivity ;;
    youtube)   PKG=com.google.android.youtube.tv; COMP=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity ;;
    globoplay) PKG=com.globo.globotv; COMP=com.globo.globotv/.splashtv.SplashActivity ;;
    spotify)   PKG=com.spotify.tv.android; COMP=com.spotify.tv.android/.SpotifyTVActivity ;;
    *) echo "app desconhecido: $APP"; exit 2 ;;
esac
OUT="medicoes/assisted/$(date +%Y%m%d-%H%M%S)-$APP-$LABEL"
mkdir -p "$OUT"
tv_connect

# Dispositivo de entrada do controle IR (o mesmo do tcl-keypath-bench.sh).
DEV=$(tv getevent -lp 2>/dev/null | awk '/^add device/ { d = $4 } /name:.*"mtkinp"/ { print d; exit }')
[ -n "$DEV" ] || { echo "receptor mtkinp nao encontrado"; exit 2; }

tv input keyevent KEYCODE_WAKEUP
sleep 3
if [ "$MODE" = frio ]; then tv am force-stop "$PKG"; sleep 2; fi

T0=$(date +%s.%N)
tv am start -W -n "$COMP" > "$OUT/am_start.txt" 2>/dev/null
WAIT=$(awk -F': ' '/^WaitTime/ { print $2 }' "$OUT/am_start.txt")
log "$APP aberto (WaitTime ${WAIT} ms)."
log ">>> Pressione QUALQUER tecla do controle na TV quando o conteudo aparecer <<<"

# Espera a primeira tecla do controle, lendo o dispositivo de entrada direto.
# Sem o 'tr' no meio: o tr bufferiza e a tecla so apareceria depois.
rm -f "$OUT/keytime.txt"
( "$ADB" -s "$SERIAL" shell "getevent -lt $DEV" < /dev/null |
  awk '/EV_KEY/ && /DOWN/ { system("date +%s.%N"); exit }' > "$OUT/keytime.txt" ) &
GPID=$!

# Espera ate 300 s, avisando a cada 30 s que ainda esta esperando.
WAITED=0
while [ ! -s "$OUT/keytime.txt" ] && [ $WAITED -lt 300 ]; do
    sleep 1
    WAITED=$((WAITED + 1))
    if [ $((WAITED % 30)) -eq 0 ]; then log "  ainda esperando uma tecla do controle (${WAITED}s)..."; fi
done
kill $GPID 2>/dev/null

if [ -s "$OUT/keytime.txt" ]; then
    KEY_T=$(cat "$OUT/keytime.txt")
    READY=$(awk -v a="$KEY_T" -v b="$T0" 'BEGIN { printf "%.1f", a - b }')
    RESULT="pronto_apos_${READY}s"
else
    READY=""
    RESULT="sem_tecla_em_300s"
fi

{
    echo "app=$APP rotulo=$LABEL modo=$MODE"
    echo "waittime_ms=$WAIT"
    echo "tempo_ate_o_conteudo_aparecer_s=${READY:-NAO_MEDIDO}"
    echo "resultado=$RESULT"
} > "$OUT/resumo.txt"
cat "$OUT/resumo.txt"
