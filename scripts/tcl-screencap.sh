#!/usr/bin/env bash
# Captura a tela da TV em PNG BINARIO (sem a corrupcao de UTF-16 do '>' do PowerShell).
#
# Uso: scripts/tcl-screencap.sh <arquivo.png> [host:porta]
#
# Por que existe: em PowerShell 5.1, `adb exec-out screencap -p > arquivo.png` converte a
# saida binaria em texto e a reassina em UTF-16; o byte de assinatura do PNG (0x89) e
# perdido e o arquivo deixa de ser PNG. Ver scripts/tcl-screencap.ps1.
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh

OUT="${1:?uso: tcl-screencap.sh <arquivo.png> [host:porta]}"
if [ $# -ge 2 ]; then SERIAL="$2"; fi
tv_connect

# O PowerShell precisa do caminho no formato do Windows; o bash usa o caminho dele.
OUT_WIN=$(cygpath -w "$OUT" 2>/dev/null || echo "$OUT")
powershell -ExecutionPolicy Bypass -File scripts/tcl-screencap.ps1 -Out "$OUT_WIN" -Serial "$SERIAL" > /dev/null
rc=$?
[ $rc -eq 0 ] || { echo "falha na captura ($OUT)"; exit $rc; }
# Confere a assinatura do PNG no arquivo gravado.
sig=$(head -c 8 "$OUT" | od -An -tx1 | tr -d ' \n')
[ "$sig" = "89504e470d0a1a0a" ] || { echo "arquivo nao e um PNG: $OUT"; exit 1; }
echo "$(wc -c < "$OUT") $OUT"
