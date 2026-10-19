#!/usr/bin/env bash
# Um passo da otimizacao: desativa UM pacote, espera, roda o smoke test e reverte sozinho se algo falhar.
# Usa tcl-pkg.ps1 (lista de protegidos + changes.log) para a alteracao em si.
#
# Uso: scripts/tcl-try-disable.sh <pacote> [teste extra...]
#      o teste extra e um comando de shell da TV que precisa terminar com sucesso (ex.: abrir um app)
#      FORCE=1 passa -Force ao tcl-pkg.ps1 (pacote protegido; so com aprovacao explicita)
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
PKG="${1:?uso: tcl-try-disable.sh <pacote> [teste extra]}"
shift
EXTRA="$*"
tv_connect

pkg_ps() { powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tcl-pkg.ps1 "$@" | tr -d '\r'; }

SINCE=$(tv date +%s)
log "Desativando $PKG"
pkg_ps disable "$PKG" ${FORCE:+-Force} || { log "tcl-pkg.ps1 recusou ou falhou; nada alterado"; exit 1; }
sleep 15

FAILED=0
# O teste extra roda antes do smoke test para que um travamento causado por ele tambem seja visto.
if [ -n "$EXTRA" ]; then
    log "Teste extra: $EXTRA"
    if tv "$EXTRA"; then echo "  OK     teste extra"; else echo "  FALHA  teste extra"; FAILED=1; fi
    sleep 5
    tv input keyevent KEYCODE_HOME
    sleep 3
fi
bash scripts/tcl-smoke.sh "$SINCE" || FAILED=1

if [ "$FAILED" -ne 0 ]; then
    log "Falha depois de desativar $PKG: revertendo"
    pkg_ps enable "$PKG"
    exit 1
fi
log "$PKG desativado e funcoes essenciais OK"
