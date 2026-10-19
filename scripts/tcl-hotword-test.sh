#!/usr/bin/env bash
# Mede o custo da escuta continua do "Ok Google" sem alterar nada: registra se o microfone embutido
# esta aberto em modo HOTWORD e mede a CPU numa tela parada (Configuracoes) 3 x 30 s.
# Rode uma vez com a deteccao ligada e outra com ela desligada; o desligar/religar e feito a parte,
# so com aprovacao (o Assistente e o botao de microfone do controle continuam funcionando).
#
# Uso: scripts/tcl-hotword-test.sh <rotulo>      (ex.: hotword-ligado, hotword-desligado)
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-hotword-test.sh <rotulo>}"
tv_connect

# Fonte 1999 = AUDIO_SOURCE_HOTWORD; o dono esperado e o com.google.android.katniss (uid 10012).
HOTWORD=$(tv dumpsys media.audio_policy | grep -c "input source: 1999")
VOICE_AP=$(tv pidof voice_ap)
log "$LABEL: capturas HOTWORD abertas = $HOTWORD | voice_ap pid = ${VOICE_AP:-nao roda}"

tv am start -W -n com.android.tv.settings/.MainSettings > /dev/null
sleep 20
for i in 1 2 3; do bash scripts/tcl-idle-probe.sh "$LABEL-$i" 30; done
tv input keyevent KEYCODE_HOME
