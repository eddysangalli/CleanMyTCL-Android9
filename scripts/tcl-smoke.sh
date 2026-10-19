#!/usr/bin/env bash
# Confere as funcoes essenciais da TCL depois de cada alteracao. Sai com codigo 1 se algo falhar.
# Abre e fecha Configuracoes, uma entrada HDMI conectada, Netflix e o Assistente; nao altera nada.
#
# Uso: scripts/tcl-smoke.sh [desde]   # desde = horario epoch da TV para procurar travamentos (padrao: 3 min atras)
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect

SINCE="${1:-$(( $(tv date +%s) - 180 ))}"
FAIL=0
ok() { echo "  OK     $*"; }
bad() { echo "  FALHA  $*"; FAIL=$((FAIL + 1)); }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }

log "Funcoes essenciais (travamentos procurados desde $(date -d "@$SINCE" '+%H:%M:%S'))"

# Travamentos e ANRs desde a alteracao. am_wtf so informa: o framework da TCL gera
# "non-protected broadcast com.tcl.Hdr" toda vez que uma entrada HDMI abre.
EVENTS=$("$ADB" -s "$SERIAL" logcat -d -b events -v epoch | tr -d '\r' | awk -v s="$SINCE" '$1 + 0 >= s')
CRASHES=$(echo "$EVENTS" | grep -E " am_(crash|anr):" || true)
WTF=$(echo "$EVENTS" | grep -E " am_wtf:" | grep -v "non-protected broadcast com.tcl.Hdr" || true)
if [ -z "$CRASHES" ]; then ok "nenhum travamento/ANR"; else bad "travamentos/ANR:"; echo "$CRASHES" | cut -c1-220 | sed 's/^/           /'; fi
[ -z "$WTF" ] || { echo "  INFO   avisos wtf:"; echo "$WTF" | cut -c1-220 | sed 's/^/           /'; }
SS_PID=$(tv pidof system_server)

# Processos centrais vivos
for p in system_server com.android.systemui com.tcl.tv com.tcl.tvinput com.tcl.framework.custom \
         com.google.android.katniss:interactor com.google.android.apps.mediashell sitatvservice audioserver \
         android.hardware.drm@1.1-service.widevine; do
    check "processo $p" "[ -n \"\$(tv pidof $p)\" ]"
done

# Rede, Bluetooth e controle remoto pareado
check "internet (ping 8.8.8.8)" "tv ping -c 2 -W 3 8.8.8.8 | grep -q ' 0% packet loss'"
BT=$(tv dumpsys bluetooth_manager)
check "Bluetooth ligado" "echo \"\$BT\" | grep -q 'enabled: true'"
check "controle TCL_RC802VLA pareado" "echo \"\$BT\" | grep -q 'TCL_RC802VLA'"

# Audio: saida no alto-falante disponivel
check "saida de audio (alto-falante)" "tv dumpsys media.audio_flinger | grep -q 'AUDIO_DEVICE_OUT_SPEAKER'"

# Home responde e mostra o launcher configurado
HOME_PKG="$(tv_home_component)"; HOME_PKG="${HOME_PKG%%/*}"
tv input keyevent KEYCODE_HOME
# A TV pode demorar alguns segundos para assentar o foco depois do HOME (o sistema
# estava carregando o Netflix). Espera ate o foco estabilizar, em vez de dormir 3 s.
for _ in $(seq 1 10); do
    sleep 1
    [ "$(tv_focus_pkg)" = "$HOME_PKG" ] && break
done
check "Home abre $HOME_PKG" "[ \"\$(tv_focus_pkg)\" = \"$HOME_PKG\" ]"

# Configuracoes
check "Configuracoes abrem" "tv am start -W -n com.android.tv.settings/.MainSettings | grep -q 'Status: ok'"
sleep 2; tv input keyevent KEYCODE_HOME; sleep 2

# Entrada conectada abre no app de TV (HDMI de preferencia, senao AV)
HDMI_HW=$(tv dumpsys tv_input | grep -oE "TvInputHardwareInfo \{id=[0-9]+, type=9" | sed -E 's/.*id=([0-9]+).*/HW\1/' | sort -u)
CONNECTED=$(tv_connected_passthrough)
INPUT=$(echo "$CONNECTED" | grep -F "$HDMI_HW" | head -1)
[ -n "$INPUT" ] || INPUT=$(echo "$CONNECTED" | head -1)
if [ -n "$INPUT" ]; then
    tv am start -a android.intent.action.VIEW -d "$(passthrough_uri "$INPUT")" -t vnd.android.cursor.item/channel > /dev/null 2>&1
    sleep 5
    check "entrada ${INPUT##*/} abre no com.tcl.tv" "[ \"\$(tv_focus_pkg)\" = com.tcl.tv ]"
    check "sessao ativa na entrada ${INPUT##*/}" \
        "tv dumpsys tv_input | sed -n '/sessionStateMap/,/mainSessionToken/p' | grep -q 'inputId: $INPUT'"
    tv input keyevent KEYCODE_HOME; sleep 3
else
    bad "nenhuma entrada HDMI com dispositivo conectado para testar"
fi

# Netflix (DRM Widevine) abre
check "Netflix abre" "tv am start -W -n com.netflix.ninja/.MainActivity | grep -q 'Status: ok'"
sleep 6; tv input keyevent KEYCODE_HOME; sleep 3

# Assistente escuta pelo microfone.
#Nesta TV o assistente leva ~10 s do KEYCODE_SEARCH ate o mic abrir e o S3 conectar,
#entao espera-se o evento em vez de dormir um tempo fixo (o sleep de 7 s dava falso negativo).
"$ADB" -s "$SERIAL" logcat -c -b main -b system
tv input keyevent KEYCODE_SEARCH
VOICE=""
for _ in $(seq 1 25); do
    sleep 1
    VOICE=$("$ADB" -s "$SERIAL" logcat -d -b main -b system | tr -d '\r')
    if printf '%s\n' "$VOICE" | grep -q 'LIBAS_S3_CONNECTION_UP'; then break; fi
done
tv input keyevent KEYCODE_BACK; sleep 2
if ! printf '%s\n' "$VOICE" | grep -q 'LIBAS_S3_CONNECTION_UP'; then
    mkdir -p private 2>/dev/null || true
    printf '%s\n' "$VOICE" > private/smoke-voice-debug.log 2>/dev/null || true
fi
check "Assistente abre e escuta" "echo \"\$VOICE\" | grep -q ASSISTANT_LISTENING_EVENT"
check "microfone abre no HAL" "echo \"\$VOICE\" | grep -q 'adev_open_input_stream: flags'"
check "Assistente conecta ao servidor" "echo \"\$VOICE\" | grep -q LIBAS_S3_CONNECTION_UP"

tv input keyevent KEYCODE_HOME
check "system_server nao reiniciou durante o teste (pid $SS_PID)" "[ \"\$(tv pidof system_server)\" = \"$SS_PID\" ]"
if [ "$FAIL" -eq 0 ]; then log "Tudo OK"; else log "$FAIL falha(s)"; fi
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
