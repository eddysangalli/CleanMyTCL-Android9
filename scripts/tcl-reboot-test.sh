#!/usr/bin/env bash
# Reinicia a TV e confere: volta do ADB, tempo de inicializacao, pacotes desativados mantidos e funcoes essenciais.
# Recusa reiniciar se o ADB por rede nao for persistente (o acesso se perderia).
#
# Uso: scripts/tcl-reboot-test.sh [rotulo]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:-reboot}"
tv_connect

PORT_PERSIST=$(tv getprop persist.adb.tcp.port)
PORT_SERVICE=$(tv getprop service.adb.tcp.port)
log "ADB por rede: persist.adb.tcp.port='$PORT_PERSIST' service.adb.tcp.port='$PORT_SERVICE'"
if [ "$PORT_PERSIST" != 5555 ] && [ "${FORCE_REBOOT:-0}" != 1 ]; then
    echo "persist.adb.tcp.port nao e 5555: o ADB por rede pode nao voltar depois do reboot. Abortado."
    exit 3
fi

BEFORE=$(tv pm list packages -d | sort)
HOME_BEFORE=$(tv_home_component)
log "Reiniciando (Home atual: $HOME_BEFORE)"
T0=$(date +%s)
"$ADB" -s "$SERIAL" reboot
sleep 20

until "$ADB" connect "$SERIAL" 2>/dev/null | grep -q "connected to" &&
      [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    if [ $(( $(date +%s) - T0 )) -gt 300 ]; then echo "TV nao voltou em 5 min"; exit 4; fi
    sleep 3
done
T_BOOT=$(( $(date +%s) - T0 ))
log "boot_completed ${T_BOOT}s apos o comando de reboot"

# Espera o Home aparecer e o sistema assentar
until [ "$(tv_focus_pkg)" = "${HOME_BEFORE%%/*}" ]; do
    if [ $(( $(date +%s) - T0 )) -gt 360 ]; then echo "Home nao apareceu"; break; fi
    sleep 2
done
T_HOME=$(( $(date +%s) - T0 ))
ENABLE_SCREEN=$(tv logcat -b events -d | grep -m1 boot_progress_enable_screen | awk -F': ' '{ print $2 }')
LAUNCH=$(tv logcat -b events -d | grep -m1 "am_activity_launch_time.*${HOME_BEFORE%%/*}" | sed -E 's/.*,([0-9]+),[0-9]+\]$/\1/')
log "Home em foco ${T_HOME}s apos o reboot | boot_progress_enable_screen ${ENABLE_SCREEN} ms | Home desenhado em ${LAUNCH:-?} ms"

AFTER=$(tv pm list packages -d | sort)
if [ "$BEFORE" = "$AFTER" ]; then
    log "Pacotes desativados mantidos ($(echo "$AFTER" | grep -c package:))"
else
    log "ATENCAO: a lista de desativados mudou no reboot"; diff <(echo "$BEFORE") <(echo "$AFTER")
fi
[ "$(tv_home_component)" = "$HOME_BEFORE" ] && log "Home inalterado" || log "ATENCAO: Home mudou para $(tv_home_component)"

sleep 60
bash scripts/tcl-smoke.sh "$T0"
SMOKE=$?
mkdir -p medicoes
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,reboot_cmd_to_boot_completed_s=$T_BOOT,home_focus_s=$T_HOME,enable_screen_ms=$ENABLE_SCREEN,home_drawn_ms=${LAUNCH:-},smoke=$SMOKE" >> medicoes/reboots.csv
exit $SMOKE
