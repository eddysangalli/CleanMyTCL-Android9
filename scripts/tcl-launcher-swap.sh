#!/usr/bin/env bash
# Troca a versao do launcher (mesmo pacote br.eddy.tvhome) por um APK salvo, para comparar versoes ou
# voltar a anterior. Confere que o Home continua no launcher e compila no perfil padrao do Android
# (speed-profile), igual para todas as versoes, antes de qualquer medicao.
#
# Uso: scripts/tcl-launcher-swap.sh medicoes/apks/tvhome-0.3.apk
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
APK="${1:?uso: tcl-launcher-swap.sh <apk>}"
tv_connect

# Versao lida de dentro do APK: um nome de arquivo errado nao passa despercebido.
AAPT2="${AAPT2:-/d/Android/Sdk/build-tools/34.0.0/aapt2.exe}"
APK_VERSION=""
[ -x "$AAPT2" ] && APK_VERSION=$("$AAPT2" dump badging "$APK" 2>/dev/null | head -1 | sed -E "s/.*versionName='([^']*)'.*/\1/")
log "Instalando $APK (versao no APK: ${APK_VERSION:-?})"
"$ADB" -s "$SERIAL" install -r "$APK" | tr -d '\r' | tail -1
INSTALLED=$(tv dumpsys package br.eddy.tvhome | grep -m1 versionName | sed 's/.*=//')
if [ -n "$APK_VERSION" ] && [ "$INSTALLED" != "$APK_VERSION" ]; then
    echo "ATENCAO: a TV ficou com $INSTALLED, mas o APK e $APK_VERSION"
    exit 1
fi
tv cmd package compile -m speed-profile -f br.eddy.tvhome > /dev/null
HOME_NOW=$(tv_home_component)
case "$HOME_NOW" in
    br.eddy.tvhome/*) ;;
    *) echo "ATENCAO: o Home agora e $HOME_NOW"; exit 1 ;;
esac
tv input keyevent KEYCODE_HOME
sleep 5
log "Instalado $(tv dumpsys package br.eddy.tvhome | grep -m1 versionName | sed 's/.*=//'); Home = $HOME_NOW; foco = $(tv_focus_pkg)"
