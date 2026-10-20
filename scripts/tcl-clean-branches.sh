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
  if git merge-base --is-ancestor "$b" develop 2>/dev/null; then
    state="ja integrada no develop"
  else
    # Os commits que a branch tem alem do develop: se todos vivem num remoto (o fork de
    # origem, ou o upstream de onde a branch foi copiada), apagar a copia local nao perde nada.
    lost=0
    for c in $(git rev-list origin/develop.."$b" 2>/dev/null); do
      if ! git branch -r --contains "$c" 2>/dev/null | grep -q . \
         && ! git branch -r --contains "$c" upstream 2>/dev/null | grep -q .; then
        lost=$((lost + 1))
      fi
    done
    if [ "$lost" = 0 ]; then
      state="copia de inspecao, tudo recuperavel num remoto"
    else
      state="ATENCAO: $lost commits que nao existem em nenhum remoto"
    fi
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
  if git merge-base --is-ancestor "$b" develop 2>/dev/null; then
    git branch -D "$b" > /dev/null 2>&1 && echo "  apagada: $b (ja no develop)"
  else
    # Só apaga quando nenhum commit existe exclusively nesta branch: o que já está no fork
    # (prfork) ou no upstream continua lá.
    lost=0
    for c in $(git rev-list origin/develop.."$b" 2>/dev/null); do
      if ! git branch -r --contains "$c" 2>/dev/null | grep -q . \
         && ! git branch -r --contains "$c" upstream 2>/dev/null | grep -q .; then
        lost=$((lost + 1))
      fi
    done
    if [ "$lost" = 0 ]; then
      git branch -D "$b" > /dev/null 2>&1 && echo "  apagada: $b (recuperavel num remoto)"
    else
      echo "  MANTIDA: $b ($lost commits so existem aqui)"
    fi
  fi
done

echo
echo "Restaram:"
git branch --format='  %(refname:short)'
