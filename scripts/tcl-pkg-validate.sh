#!/usr/bin/env bash
# Validacao de nomes de pacote antes de qualquer comando na TV.
#
# Por que: um arquivo de debloat e texto. Se ele vier de fora (ou for editado com erro de digitacao),
# o nome do pacote pode conter metacaracteres de shell. Sem validacao, `pm disable-user --user 0 <nome>`
# vira uma linha de shell completa no aparelho e pode executar o que estiver no resto da linha.
# Ver https://github.com/Gathin23/cleanyourtv/issues/165
#
# Um nome de pacote Android valido:
#   - comeca com uma letra
#   - tem ao menos um ponto separando segmentos
#   - cada segmento comeca com uma letra
#   - so pode conter letras, digitos, ponto e underscore
#   - no maximo 255 caracteres
#
# Uso:
#   . scripts/tcl-pkg-validate.sh
#   validate_package_name "com.tcl.bi"     # 0 = ok, 1 = invalido (msg em stderr)
#   validate_package_list "lista.txt"      # valida todas as linhas; 1 =alguma invalida
#   filter_valid_package_list "lista.txt"  # imprime so as linhas validas
set -uo pipefail

# Valida um unico nome de pacote. Escreve o motivo no stderr e devolve 1 se for invalido.
validate_package_name() {
    local name="${1:-}"
    if [ -z "$name" ]; then
        echo "nome de pacote vazio" >&2
        return 1
    fi
    if [ "${#name}" -gt 255 ]; then
        echo "nome de pacote longo demais (${#name} > 255): $name" >&2
        return 1
    fi
    # Quebra de linha: o grep de baixo e linha a linha, entao "com.a\nid" passaria no teste
    # de regex. Recusa antes. $'\n' e o quoting ANSI-C do bash (nao usar $(printf), que
    # remove a quebra de linha e viraria um padrao vazio).
    local NL=$'\n' CR=$'\r'
    if [[ "$name" == *"$NL"* || "$name" == *"$CR"* ]]; then
        echo "nome de pacote com quebra de linha: $name" >&2
        return 1
    fi
    # Segmento = parte entre pontos. Cada um comeca com letra e so tem letra/digito/underscore.
    if ! printf '%s' "$name" | grep -Eq '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$'; then
        echo "nome de pacote invalido (use apenas letras, digitos, ponto e underscore; cada segmento comeca com letra): $name" >&2
        return 1
    fi
    return 0
}

# Valida um arquivo de debloat inteiro (uma linha por pacote, '#' comenta).
# Devolve 1 se alguma linha for invalida e lista as offending lines no stderr.
validate_package_list() {
    local list="${1:?uso: validate_package_list <arquivo>}"
    local bad=0 line name
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        # remove espacos nas pontas
        line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
        [ -z "$line" ] && continue
        name="$line"
        if ! validate_package_name "$name"; then
            bad=1
        fi
    done < "$list"
    return "$bad"
}

# Imprime as linhas validas de um arquivo de debloat (util para gerar uma lista limpa).
filter_valid_package_list() {
    local list="${1:?uso: filter_valid_package_list <arquivo>}"
    local line name
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
        [ -z "$line" ] && continue
        name="$line"
        if validate_package_name "$name" >/dev/null 2>&1; then
            printf '%s\n' "$name"
        fi
    done < "$list"
    return 0
}
