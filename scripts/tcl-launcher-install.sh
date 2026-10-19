#!/usr/bin/env bash
# Compila o launcher (launcher/) e instala na TV como um app comum: NAO vira o Home e NAO desativa o
# launcher original (o tvlauncher tem prioridade 2 e continua respondendo ao botao Home).
# Abrir para testar:  adb shell am start -n br.eddy.tvhome/com.gathin.tvhome.MainActivity
# Remover:            adb uninstall br.eddy.tvhome
#
# Requer JAVA_HOME (JDK 17) e ANDROID_HOME (platform 34, build-tools 34.0.0).
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
: "${JAVA_HOME:?defina JAVA_HOME com um JDK 17}"
: "${ANDROID_HOME:?defina ANDROID_HOME com o Android SDK}"
tv_connect

HOME_BEFORE=$(tv_home_component)
log "Compilando (Home atual: $HOME_BEFORE)"
echo "sdk.dir=$(cygpath -m "$ANDROID_HOME")" > launcher/local.properties
# Release: sem o modo depuravel, o ART otimiza o codigo como faria com qualquer app instalado.
(cd launcher && ./gradlew assembleRelease -q --console=plain)

APK=launcher/app/build/outputs/apk/release/app-release.apk
log "Instalando $APK ($(du -h "$APK" | cut -f1))"
"$ADB" -s "$SERIAL" install -r "$APK"

HOME_AFTER=$(tv_home_component)
if [ "$HOME_AFTER" != "$HOME_BEFORE" ]; then
    echo "ATENCAO: o Home mudou para $HOME_AFTER"
    exit 1
fi
log "Instalado. O Home continua $HOME_AFTER"
