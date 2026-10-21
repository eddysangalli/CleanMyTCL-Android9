#!/usr/bin/env bash
# Quantas Views o launcher infla no cold start, e quanto de GPU/PSS ele usa.
#
# O PR #446 do upstream (grade em uma passada) promete menos Views infladas e menos memoria de GPU.
# Esses numeros nao existiam para a nossa TV, so para outros aparelhos. Este script mede aqui:
# quantas Views ficam vivas depois que a Home assenta, quantos contextos de app, o PSS total e o
# cache de GPU do Skia.
#
# O metodo e o do teste do upstream (comentario do priku no PR #446): fechar a Home a forca, abrir,
# esperar a tela assentar, andar com o foco para exercitar a grade e so entao ler o meminfo/gfxinfo.
#
# Uso: scripts/tcl-view-count.sh <rotulo> [movimentos=10]
# Saida: medicoes/viewcount/<data-hora>-<rotulo>/ (meminfo.txt, gfxinfo.txt, resumo.txt)
#        e uma linha em medicoes/viewcount.csv
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-view-count.sh <rotulo> [movimentos]}"
MOVES="${2:-10}"
OUT="medicoes/viewcount/$(date +%Y%m%d-%H%M%S)-$LABEL"
mkdir -p "$OUT"
tv_connect

PKG=br.eddy.tvhome

# O force-stop abaixo apaga a lista de servicos de acessibilidade nesta TCL: guarda e devolve no fim.
tv_acc_save
trap tv_acc_restore EXIT

# Fechar a Home a forca: a proxima abertura e um cold start de verdade.
"$ADB" -s "$SERIAL" shell am force-stop "$PKG" > /dev/null 2>&1
sleep 2
"$ADB" -s "$SERIAL" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1
sleep 6

# Ponto 1: logo depois do primeiro layout, sem navegar. E aqui que o PR #446 do upstream mede
# (125 Views contra 61): o numero de Views infladas na primeira passagem da grade.
"$ADB" -s "$SERIAL" shell dumpsys meminfo "$PKG" > "$OUT/meminfo-inicial.txt" 2>&1
"$ADB" -s "$SERIAL" shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo-inicial.txt" 2>&1

# Andar com o foco: sem isto a grade fica so com a linha do dock inflada.
for i in $(seq 1 "$MOVES"); do
    tv input keyevent KEYCODE_DPAD_RIGHT > /dev/null 2>&1
    sleep 0.4
done
for i in $(seq 1 "$MOVES"); do
    tv input keyevent KEYCODE_DPAD_LEFT > /dev/null 2>&1
    sleep 0.4
done
sleep 3

"$ADB" -s "$SERIAL" shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1
"$ADB" -s "$SERIAL" shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1

# Le as duas capturas. Um valor por campo: o do inicio e o de depois de navegar.
read_field() {  # <arquivo> <rotulo>
    grep -oE "$2:[ ]*[0-9]+" "$1" | head -1 | grep -oE "[0-9]+"
}
VIEWS_INIT=$(read_field "$OUT/meminfo-inicial.txt" "Views")
VIEWS=$(read_field "$OUT/meminfo.txt" "Views")
CONTEXTS=$(read_field "$OUT/meminfo.txt" "AppContexts")
ACTIVITIES=$(read_field "$OUT/meminfo.txt" "Activities")
# O PSS total e a linha "TOTAL:" do quadro do meminfo (em KB). O "Graphics: 0" do resumo
# nao serve: o Android 9 nao expoe a memoria de GPU por ali (so o gfxinfo expoe).
PSS_KB=$(grep -E "^ +TOTAL +[0-9]+" "$OUT/meminfo.txt" | head -1 | awk '{print $2}')
# O cache de GPU do Skia aparece em "GPU Caches: RenderTarget: X MB (N entries)".
GPU=$(grep -oE "RenderTarget: [0-9.]+ MB" "$OUT/gfxinfo.txt" | head -1 | grep -oE "[0-9.]+")
GPU_ENTRIES=$(grep -oE "RenderTarget: [0-9.]+ MB \([0-9]+ entries\)" "$OUT/gfxinfo.txt" | head -1 | grep -oE "\([0-9]+ entries\)" | grep -oE "[0-9]+")
JANKY=$(grep -oE "Janky frames: [0-9]+ \([0-9.]+%\)" "$OUT/gfxinfo.txt" | head -1)
[ -n "$VIEWS_INIT" ] || VIEWS_INIT=NA
[ -n "$VIEWS" ] || VIEWS=NA
[ -n "$CONTEXTS" ] || CONTEXTS=NA
[ -n "$ACTIVITIES" ] || ACTIVITIES=NA
[ -n "$PSS_KB" ] || PSS_KB=NA
[ -n "$GPU" ] || GPU=NA
[ -n "$GPU_ENTRIES" ] || GPU_ENTRIES=NA
[ -n "$JANKY" ] || JANKY=NA

{
    echo "rotulo:          $LABEL"
    echo "data:            $(date '+%Y-%m-%d %H:%M')"
    echo "Views (1o layout): $VIEWS_INIT"
    echo "Views (pos-nav):   $VIEWS"
    echo "AppContexts:     $CONTEXTS"
    echo "Activities:      $ACTIVITIES"
    echo "TOTAL PSS:       $PSS_KB KB"
    echo "RenderTarget:    $GPU MB ($GPU_ENTRIES entradas)"
    echo "Janky frames:    $JANKY"
    echo "movimentos:      $MOVES para a direita e $MOVES para a esquerda"
} | tee "$OUT/resumo.txt"

[ -f medicoes/viewcount.csv ] || echo "data,rotulo,views_inicial,views_pos_nav,appcontexts,activities,pss_kb,rendertarget_mb,rendertarget_entries,janky" > medicoes/viewcount.csv
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$VIEWS_INIT,$VIEWS,$CONTEXTS,$ACTIVITIES,$PSS_KB,$GPU,$GPU_ENTRIES,$JANKY" >> medicoes/viewcount.csv
log "Pronto: $OUT"
