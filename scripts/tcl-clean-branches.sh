#!/usr/bin/env bash
# Limpa os branches locais de inspecao, depois de confirmar que nada que eles Guards precisa
# esta no develop. Os branches de trabalho (develop, main) e as branches que apontam para
# commits nao publicados ficam de fora.
#
# Uso: scripts/tcl-clean-branches.sh [--dry-run]
set -uo pipefail
cd "$(dirname "$0")/.."
DRY=0
[ "${1:-}" = "--dry-run" ] && DRY=1

# O que NUNCA e apagado: o trabalho em andamento e as referencias de medicao.
KEEP="develop
main
measure/old-spring"

# Por que cada um pode ir: ou ja foi integrado no develop, ou e uma copia de auditoria de
# um PR do upstream (o conteudo util ja foi portado ou enviado), ou a base de um PR ja
# publicado no fork.
REASON_KEPT="develop/main: trabalho em andamento
measure/old-spring: a build da mola antiga, guardada em medicoes/apks/"

echo "Branches mantidos:"
echo "$REASON_KEPT"
echo

CANDIDATES=$(git branch --format='%(refname:short)' | grep -vE '^(develop|main|measure/old-spring)$')
echo "Candidates para apagar:"
for b in $CANDIDATES; do
  # A branch e integrated? some dos commits dela ja estao no develop?
  if git merge-base --is-ancestor "$b" develop 2>/dev/null; then
    state="ja integrada no develop"
  else
    state="copia de inspecao (conteudo portado ou enviado)"
  fi
  echo "  $b  -- $state"
done

if [ "$DRY" = 1 ]; then
  echo
  echo "--dry-run: nada foi apagado."
  exit 0
fi

echo
echo "Apagando..."
for b in $CANDIDATES; do
  # Nunca apaga uma branch que tem trabalho nao publicado em develop.
  if git merge-base --is-ancestor "$b" develop 2>/dev/null; then
    git branch -D "$b" > /dev/null 2>&1 && echo "  apagada: $b"
  else
    # Verifica se todos os commits dela ja estao no develop ou no main.
    ahead=$(git rev-list --count develop.."$b" 2>/dev/null || echo 0)
    behind_main=$(git rev-list --count main.."$b" 2>/dev/null || echo 0)
    if [ "$ahead" = 0 ] || [ "$behind_main" = 0 ]; then
      git branch -D "$b" > /dev/null 2>&1 && echo "  apagada: $b (nada unpublished)"
    else
      echo "  MANTIDA: $b (tem $ahead commits fora do develop)"
    fi
  fi
done

echo
echo "Restaram:"
git branch --format='  %(refname:short)'
