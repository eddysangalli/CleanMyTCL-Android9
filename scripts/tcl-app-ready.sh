#!/usr/bin/env bash
# Descobre quando a tela do app fica INTERATIVA, sem depender de pixels.
# A captura de tela nao serve em superficies DRM (Netflix, DRM do Widevine): o screencap
# devolve vazio. O 'uiautomator dump' le a arvore de views, que funciona mesmo assim.
#
# Criterio de "pronto": a hierarquia contem um no com focused="true" (o app ja colocou o
# foco em um item, ou seja, da para navegar) OU um no com texto/conteudo preenchido.
#
# Uso: scripts/tcl-app-ready.sh <app> <rotulo> [segundos=180] [frio|quente]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh

APP="${1:?uso: tcl-app-ready.sh <netflix|youtube|globoplay|spotify> <rotulo> [seg] [frio|quente]}"
LABEL="${2:?rotulo}"
SECS="${3:-180}"
MODE="${4:-frio}"
case "$APP" in
    netflix)   PKG=com.netflix.ninja; COMP=com.netflix.ninja/.MainActivity ;;
    youtube)   PKG=com.google.android.youtube.tv; COMP=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity ;;
    globoplay) PKG=com.globo.globotv; COMP=com.globo.globotv/.splashtv.SplashActivity ;;
    spotify)   PKG=com.spotify.tv.android; COMP=com.spotify.tv.android/.SpotifyTVActivity ;;
    settings)  PKG=com.android.tv.settings; COMP=com.android.tv.settings/.MainSettings ;;
    *) echo "app desconhecido: $APP"; exit 2 ;;
esac
OUT="medicoes/app-ready/$(date +%Y%m%d-%H%M%S)-$APP-$LABEL"
mkdir -p "$OUT"
tv_connect

tv input keyevent KEYCODE_WAKEUP
sleep 3
if [ "$MODE" = frio ]; then tv am force-stop "$PKG"; sleep 2; fi

T0=$(date +%s.%N)
tv am start -W -n "$COMP" > "$OUT/am_start.txt" 2>/dev/null
WAIT=$(awk -F': ' '/^WaitTime/ { print $2 }' "$OUT/am_start.txt")
log "$APP aberto, WaitTime ${WAIT} ms"

READY=""
i=0
while [ $i -lt $((SECS / 2)) ]; do
    el=$(awk -v a="$(date +%s.%N)" -v b="$T0" 'BEGIN { printf "%.1f", a - b }')
    XML=$(tv "uiautomator dump /sdcard/ui-ready.xml >/dev/null 2>&1; cat /sdcard/ui-ready.xml" | tr -d '\r')
    FOCUSED=$(printf '%s' "$XML" | grep -c 'focused="true"')
    WITHTEXT=$(printf '%s' "$XML" | grep -oE 'text="[^"]+"|content-desc="[^"]+"' | grep -c '[^"]' || true)
    PKGOK=$(printf '%s' "$XML" | grep -c "$PKG")
    printf '%s\t%s\t%s\t%s\n' "$el" "$FOCUSED" "$WITHTEXT" "$PKGOK" >> "$OUT/poll.tsv"
    if [ "$FOCUSED" -gt 0 ] && [ "$PKGOK" -gt 0 ]; then
        READY="$el"
        printf '%s' "$XML" > "$OUT/ui-quando-pronto.xml"
        break
    fi
    i=$((i + 1))
    sleep 2
done

if [ -n "$READY" ]; then
    echo "app=$APP rotulo=$LABEL modo=$MODE waittime_ms=$WAIT pronto_interativo_s=$READY" > "$OUT/resumo.txt"
else
    echo "app=$APP rotulo=$LABEL modo=$MODE waittime_ms=$WAIT pronto_interativo_s=NAO_DETECTADO_EM_${SECS}s" > "$OUT/resumo.txt"
fi
cat "$OUT/resumo.txt"
log "Pronto: $OUT"
