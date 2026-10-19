#!/usr/bin/env bash
# Testa o launcher br.eddy.tvhome com teclas injetadas (os mesmos codigos do controle): os apps do dock,
# Entradas > HDMI conectada, botao Configuracoes e Voltar. Funciona com ele sendo ou nao o Home.
# Sai com codigo 1 se algo falhar.
#
# Uso: scripts/tcl-launcher-test.sh
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect
L=br.eddy.tvhome/com.gathin.tvhome.MainActivity
FAIL=0
ok() { echo "  OK     $*"; }
bad() { echo "  FALHA  $*"; FAIL=$((FAIL + 1)); }
open_l() { tv am start -n "$L" > /dev/null 2>&1; sleep 4; }
SINCE=$(tv date +%s)

log "Launcher: dock, entradas, configuracoes"
# O launcher volta ao ultimo app focado; 4x Esquerda leva ao inicio do dock antes de cada teste.
for t in "Netflix||com.netflix.ninja" "YouTube|22|com.google.android.youtube.tv" \
         "Globoplay|22 22|com.globo.globotv" "Spotify|22 22 22|com.spotify.tv.android"; do
    IFS='|' read -r name keys pkg <<< "$t"
    open_l
    tv input keyevent 21 21 21 21 $keys
    sleep 1
    tv input keyevent KEYCODE_DPAD_CENTER
    # Apps lentos (o Globoplay leva 5-20 s) passam por uma janela sem foco entre a abertura e a tela principal.
    for w in $(seq 1 10); do sleep 2; f=$(tv_focus_pkg); [ "$f" = "$pkg" ] && break; done
    if [ "$f" = "$pkg" ]; then ok "$name abre pelo dock"; else bad "$name: foco em $f"; fi
    tv input keyevent KEYCODE_HOME
    sleep 3
done

# Entradas: a primeira HDMI com aparelho conectado, pela posicao dela na lista do menu
HDMI_HW=$(tv dumpsys tv_input | grep -oE "TvInputHardwareInfo \{id=[0-9]+, type=9, [^}]*hdmi_port=[0-9]+" | sed -E 's/.*id=([0-9]+).*hdmi_port=([0-9]+)/\2 HW\1/' | sort -n | uniq)
TARGET=""; ROW=0
while read -r port hw; do
    if tv_connected_passthrough | grep -q "$hw\$"; then TARGET=$hw; break; fi
    ROW=$((ROW + 1))
done <<< "$HDMI_HW"
if [ -n "$TARGET" ]; then
    open_l
    tv input keyevent 19 21 21 21
    sleep 1
    tv input keyevent KEYCODE_DPAD_CENTER
    sleep 3
    [ "$ROW" -gt 0 ] && tv input keyevent $(printf '20 %.0s' $(seq 1 $ROW))
    sleep 1
    tv input keyevent KEYCODE_DPAD_CENTER
    sleep 6
    s=$(tv dumpsys tv_input | sed -n '/sessionStateMap/,/mainSessionToken/p' | grep -m1 "inputId:")
    if [ "$(tv_focus_pkg)" = com.tcl.tv ] && echo "$s" | grep -q "$TARGET"; then ok "Entradas > HDMI ($TARGET)"; else bad "HDMI: foco $(tv_focus_pkg), $s"; fi
    tv input keyevent KEYCODE_HOME
    sleep 3
else
    echo "  INFO   nenhuma HDMI com aparelho conectado"
fi

open_l
tv input keyevent 19 22 22 22
sleep 1
tv input keyevent KEYCODE_DPAD_CENTER
sleep 4
if [ "$(tv_focus_pkg)" = com.android.tv.settings ]; then ok "botao Configuracoes"; else bad "Configuracoes: foco $(tv_focus_pkg)"; fi
tv input keyevent KEYCODE_HOME
sleep 3

open_l
tv input keyevent KEYCODE_BACK
sleep 2
if [ "$(tv_focus_pkg)" = br.eddy.tvhome ]; then ok "Voltar mantem a tela inicial"; else bad "Voltar levou a $(tv_focus_pkg)"; fi

CRASH=$("$ADB" -s "$SERIAL" logcat -d -b events -v epoch | tr -d '\r' | awk -v s="$SINCE" '$1 + 0 >= s' | grep -E " am_(crash|anr):" | grep -c "br.eddy.tvhome")
if [ "$CRASH" -eq 0 ]; then ok "launcher sem travamentos"; else bad "launcher travou $CRASH vez(es)"; fi
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
