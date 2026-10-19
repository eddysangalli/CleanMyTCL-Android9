#!/usr/bin/env bash
# Limpeza de apps aprovada na etapa 5 (etapa5/PROPOSTA-APPS.md, escolhas do usuario em 19/10/2026).
#   Apps da Play Store: desinstalados, com o APK guardado antes em backup/apks/<pacote>/ (reinstala igual;
#   os dados do app se perdem).
#   Apps de sistema: so desativados, um por vez, com o teste de fumaca (tcl-try-disable.sh), que reverte
#   sozinho se algo falhar.
#
# Uso: scripts/tcl-cleanup-apps.sh backup            guarda os APKs (nao muda nada na TV)
#      scripts/tcl-cleanup-apps.sh uninstall         desinstala os apps da Play Store (exige o backup)
#      scripts/tcl-cleanup-apps.sh disable           desativa os apps de sistema, um por vez, com teste
#      scripts/tcl-cleanup-apps.sh reinstall <pkg>   reinstala um app a partir do backup
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh

# Ficam: NAVEGADOR WEB (com.internet.tvbrowser) e Receptor AirMirror (com.boost.airplay.receiver).
UNINSTALL="com.tvwebbrowser.v22 com.tv.casttv com.tm.replica com.cetusplay.remoteservice flar2.homebutton
com.irishin.buttonsremapper com.archos.mediacenter.videofree com.m4rk3t.libcopy2 com.spotify.tv.android
com.tcl.dashboard"
# Do mais isolado para o mais ligado ao resto do sistema.
DISABLE="com.tcl.tvweishi com.tcl.tv.tclhome_master com.tcl.notereminder com.tcl.waterfall.overseas
com.tcl.partnercustomizer com.tcl.messagebox com.tcl.usercenter com.tcl.smartalexa
com.tcl.MultiScreenInteraction_TV"

BK=backup/apks
installed() { tv pm list packages "$1" | grep -qx "package:$1"; }

tv_connect
case "${1:-}" in
backup)
    for p in $UNINSTALL; do
        installed "$p" || { log "$p nao esta instalado"; continue; }
        mkdir -p "$BK/$p"
        tv dumpsys package "$p" | grep -m1 versionName | tr -d ' ' > "$BK/$p/version.txt"
        for apk in $(tv pm path "$p" | sed 's/^package://'); do
            "$ADB" -s "$SERIAL" pull "$apk" "$BK/$p/" < /dev/null > /dev/null || { log "FALHA ao copiar $apk"; exit 1; }
        done
        log "$p: $(ls "$BK/$p" | grep -c '\.apk$') APK(s), $(du -sh "$BK/$p" | cut -f1), $(cat "$BK/$p/version.txt")"
    done
    ;;
uninstall)
    for p in $UNINSTALL; do
        installed "$p" || { log "$p ja nao esta instalado"; continue; }
        ls "$BK/$p"/*.apk > /dev/null 2>&1 || { log "sem backup de $p; rode 'backup' antes"; exit 1; }
        R=$(tv pm uninstall "$p")
        log "$p: $R"
        printf '%s\tuninstall\t%s\t%s\tetapa5, APK em %s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$p" "$R" "$BK/$p" >> backup/changes.log
    done
    ;;
disable)
    for p in $DISABLE; do
        if tv pm list packages -d | grep -qx "package:$p"; then log "$p ja desativado"; continue; fi
        scripts/tcl-try-disable.sh "$p" || { log "PAROU em $p (o teste falhou e o pacote foi reativado)"; exit 1; }
    done
    ;;
reinstall)
    p="${2:?informe o pacote}"
    "$ADB" -s "$SERIAL" install-multiple -r "$BK/$p"/*.apk && \
        printf '%s\treinstall\t%s\tOK\tdo backup %s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$p" "$BK/$p" >> backup/changes.log
    ;;
*)
    sed -n '2,13p' "$0"; exit 2 ;;
esac
