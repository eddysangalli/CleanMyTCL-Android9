#!/usr/bin/env bash
# Roda o teste de reinicio N vezes, cada uma com o seu proprio rotulo.
# Uso: scripts/tcl-reboot-test.sh <rotulo> [vezes]
set -uo pipefail
cd "$(dirname "$0")/.."
LABEL="${1:?uso: tcl-reboot-test.sh <rotulo> [vezes]}"
ROUNDS="${2:-1}"
for r in $(seq 1 "$ROUNDS"); do
  echo "=== RODADA $r de $ROUNDS ($LABEL) ==="
  bash scripts/tcl-reboot-test.sh "$LABEL-r$r" 2>&1 |
    grep -E 'boot_completed|Home em foco|Pacotes desativados|Home inalterado|ATENCAO|Tudo OK|FALHA|ERRO'
done
