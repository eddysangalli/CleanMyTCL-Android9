#!/usr/bin/env bash
# Testes da validacao de nomes de pacote (issue upstream #165).
# Roda sem TV e sem ADB: so a funcao de validacao.
#   bash tests/test_pkg_validate.sh
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-pkg-validate.sh

PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); printf '  OK     %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  FALHA  %s\n' "$1"; }

# Deve ser aceito.
expect_valid() {
    if validate_package_name "$1" >/dev/null 2>&1; then ok "aceita: $1"; else bad "deveria aceitar: $1"; fi
}
# Deve ser recusado.
expect_invalid() {
    if validate_package_name "$1" >/dev/null 2>&1; then bad "deveria recusar: $1"; else ok "recusa: $1"; fi
}

echo "nomes validos"
expect_valid "com.tcl.bi"
expect_valid "com.tcl.tv.tclhome_passive"
expect_valid "com.google.android.leanbacklauncher.recommendations"
expect_valid "com.example.app2"
expect_valid "com.example.my_app"
expect_valid "a.b"

echo "nomes invalidos"
expect_invalid ""                                  # vazio
expect_invalid "com"                               # sem ponto
expect_invalid ".com.tcl"                          # comeca com ponto
expect_invalid "com..tcl"                          # segmento vazio
expect_invalid "com.1tcl"                          # segmento comeca com digito
expect_invalid "com.tcl.bi "                       # espaco no fim
expect_invalid "com tcl bi"                        # espaco no meio
expect_invalid "com.tcl-bi"                        # hifen
expect_invalid "com.tcl/bi"                        # barra
expect_invalid "com.tcl.bi;rm -rf /sdcard"         # injecao de comando
expect_invalid "com.tcl.bi&&id"                    # encadeamento
expect_invalid 'com.tcl.bi`id`'                    # substituicao de comando
expect_invalid 'com.tcl.bi$(id)'                   # expansao de comando
expect_invalid "com.tcl.bi|id"                     # pipe
expect_invalid "com.tcl.bi>"                       # redirecionamento
expect_invalid "com.tcl.bi#"                       # comentario inline solto
expect_invalid "../etc/passwd"                     # caminho
expect_invalid "\$(id)"                            # so expansao
expect_invalid "com.tcl.bi
id"                                        # quebra de linha

echo "lista inteira"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/good.txt" <<'EOF'
# comentario
com.tcl.bi
com.tcl.tv.tclhome_passive    # comentario no fim

com.google.android.feedback
EOF
cat > "$TMP/bad.txt" <<'EOF'
com.tcl.bi
com.tcl.bi;rm -rf /sdcard
EOF

if validate_package_list "$TMP/good.txt" >/dev/null 2>&1; then ok "lista boa aceita"; else bad "lista boa recusada"; fi
if validate_package_list "$TMP/bad.txt" >/dev/null 2>&1; then bad "lista com injecao deveria ser recusada"; else ok "lista com injecao recusada"; fi

OUT="$(filter_valid_package_list "$TMP/bad.txt")"
if [ "$OUT" = "com.tcl.bi" ]; then ok "filtro mantem so a linha valida"; else bad "filtro inesperado: '$OUT'"; fi

echo "nomes da lista de verdade do projeto"
if validate_package_list "devices/tcl-beyondtv2-rtd2851-r851t02-v660/debloat.txt" >/dev/null 2>&1; then
    ok "debloat.txt do projeto passa na validacao"
else
    bad "debloat.txt do projeto FALHOU na validacao"
fi

echo
echo "resultado: $PASS passaram, $FAIL falharam"
[ "$FAIL" -eq 0 ]
