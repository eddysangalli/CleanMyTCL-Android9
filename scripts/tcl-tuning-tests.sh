#!/usr/bin/env bash
# Mede dois ajustes e desfaz os dois no fim:
#  1. escalas de animacao do sistema em 0,5x (janela, transicao, animadores) -> volta a 1,0x / padrao
#  2. modo ultraleve do launcher (opcoes do Inicio) -> desligado de novo
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect

toggle_light() {   # tecla de menu em Configuracoes, 1a opcao = Modo ultraleve
    tv input keyevent KEYCODE_HOME; sleep 3
    tv input keyevent 21 21 21 21 19 22 22 22; sleep 1
    tv input keyevent KEYCODE_MENU; sleep 2
    tv input keyevent KEYCODE_DPAD_CENTER; sleep 2
    tv input keyevent KEYCODE_HOME; sleep 2
}

log "Animacoes do sistema em 0,5x"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do tv settings put global $k 0.5; done
bash scripts/tcl-launcher-bench.sh v0.3.1-anim0.5
log "Voltando as animacoes ao padrao (1,0x)"
tv settings put global window_animation_scale 1.0
tv settings put global transition_animation_scale 1.0
tv settings delete global animator_duration_scale > /dev/null
echo "  agora: $(tv settings get global window_animation_scale) $(tv settings get global transition_animation_scale) $(tv settings get global animator_duration_scale)"

log "Modo ultraleve"
toggle_light
bash scripts/tcl-launcher-bench.sh v0.3.1-ultraleve
log "Desligando o modo ultraleve"
toggle_light
