#!/usr/bin/env bash
# Testa os recursos da v0.3 com teclas injetadas (menu = tecla de opcoes; segurar OK so com o controle real) e deixa tudo como estava: menu do app (segurar OK),
# mover no dock e desfazer, opcoes do Inicio, botao Voz e modo ultraleve liga/desliga.
# Capturas em etapa2/features/. Sai com codigo 1 se algo falhar.
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect
SHOTS=etapa2/features
mkdir -p "$SHOTS"
FAIL=0
ok() { echo "  OK     $*"; }
bad() { echo "  FALHA  $*"; FAIL=$((FAIL + 1)); }
shot() { scripts/tcl-screencap.sh "$SHOTS/$1.png" "$SERIAL" > /dev/null 2>&1; }
focus() { tv dumpsys window | grep -m1 mCurrentFocus | sed 's/.*u0 //; s/}$//'; }
home() { tv input keyevent KEYCODE_HOME; sleep 3; }
dock_start() { home; tv input keyevent 21 21 21 21; sleep 1; }

log "Recursos da v0.3"
# 1. Menu do app no 1o do dock: Mover para a direita, Remover do dock, Informacoes
dock_start
tv input keyevent KEYCODE_MENU
sleep 2
f=$(focus); shot 1-menu-app
case "$f" in PopupWindow*) ok "segurar OK no bloco abre o menu do app";; *) bad "menu do app nao abriu (foco: $f)";; esac
tv input keyevent KEYCODE_DPAD_CENTER   # 1a opcao: Mover para a direita
sleep 2; shot 2-dock-movido
# Desfaz: o app movido continua focado (agora na 2a posicao); 1a opcao = Mover para a esquerda
tv input keyevent KEYCODE_MENU
sleep 2
tv input keyevent KEYCODE_DPAD_CENTER
sleep 2; shot 3-dock-restaurado
ok "mover no dock e desfazer executados (conferir 2-dock-movido e 3-dock-restaurado)"

# 2. Opcoes do Inicio: segurar OK em Configuracoes
dock_start
tv input keyevent 19 22 22 22
sleep 1
tv input keyevent KEYCODE_MENU
sleep 2
f=$(focus); shot 4-opcoes-inicio
case "$f" in PopupWindow*) ok "segurar OK em Configuracoes abre as opcoes do Inicio";; *) bad "opcoes nao abriram (foco: $f)";; esac

# 3. Modo ultraleve: o menu tem Nome, Mensagem, Cor de destaque e Movimento suave.
#    "Movimento suave" e o 4o item; o rotulo mostra o estado atual. Desce 3x antes do OK.
#    Mede os quadros de um movimento e restaura o estado anterior.
tv input keyevent 20 20 20
sleep 1
shot 4b-opcoes-movimento
tv input keyevent KEYCODE_DPAD_CENTER
sleep 2
dock_start; tv input keyevent 22; sleep 2; shot 5-ultraleve
tv dumpsys gfxinfo br.eddy.tvhome reset > /dev/null
tv input keyevent 22
sleep 2
FR=$(tv dumpsys gfxinfo br.eddy.tvhome | awk -F': ' '/Total frames rendered/ { print $2; exit }')
echo "  INFO   ultraleve: quadros para 1 movimento = $FR"
tv input keyevent 19 22 22 22; sleep 1
tv input keyevent KEYCODE_MENU; sleep 2
tv input keyevent 20 20 20; sleep 1
tv input keyevent KEYCODE_DPAD_CENTER   # restaura o estado anterior
sleep 2
dock_start; tv input keyevent 22; sleep 2; shot 6-normal-de-novo
tv dumpsys gfxinfo br.eddy.tvhome reset > /dev/null
tv input keyevent 22
sleep 2
FN=$(tv dumpsys gfxinfo br.eddy.tvhome | awk -F': ' '/Total frames rendered/ { print $2; exit }')
echo "  INFO   normal: quadros para 1 movimento = $FN"
if [ "${FR:-999}" -lt "${FN:-0}" ]; then ok "ultraleve desenha menos quadros ($FR contra $FN)"; else bad "ultraleve nao reduziu os quadros ($FR contra $FN)"; fi

# 4. Botao Voz abre o Assistente atual
dock_start
"$ADB" -s "$SERIAL" logcat -c -b main
tv input keyevent 19 21 21 21 22
sleep 1
tv input keyevent KEYCODE_DPAD_CENTER
sleep 6
if "$ADB" -s "$SERIAL" logcat -d -b main | grep -q ASSISTANT_LISTENING_EVENT; then ok "botao Voz abre o Assistente escutando"; else bad "botao Voz nao abriu o Assistente"; fi
shot 7-voz
tv input keyevent KEYCODE_BACK
sleep 2
home
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
