#!/usr/bin/env bash
# Mede a TCL de forma repetivel: memoria, CPU com a TV parada, abertura de apps, volta ao Home,
# fluidez da navegacao no Home e ativacao do Assistente. Nao altera nada na TV (so abre e fecha apps).
#
# Uso:   scripts/tcl-bench.sh <rotulo> [repeticoes=3]
#        BENCH_LAUNCHER=br.eddy.tvhome/com.gathin.tvhome.MainActivity scripts/tcl-bench.sh <rotulo>
#        (mede outro launcher abrindo-o direto, sem que ele seja o Home do sistema)
# Saida: medicoes/<data-hora>-<rotulo>/resumo.txt + dados brutos, e uma linha em medicoes/historico.csv
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh

LABEL="${1:?uso: tcl-bench.sh <rotulo> [repeticoes]}"
N="${2:-3}"
OUT="medicoes/$(date +%Y%m%d-%H%M%S)-$LABEL"
mkdir -p "$OUT"
tv_connect

APPS=(
    "settings|com.android.tv.settings|com.android.tv.settings/.MainSettings"
    "youtube|com.google.android.youtube.tv|com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity"
    "netflix|com.netflix.ninja|com.netflix.ninja/.MainActivity"
    "globoplay|com.globo.globotv|com.globo.globotv/.splashtv.SplashActivity"
    "spotify|com.spotify.tv.android|com.spotify.tv.android/.SpotifyTVActivity"
)
LAUNCHER="${BENCH_LAUNCHER:-}"
HOME_COMPONENT="${LAUNCHER:-$(tv_home_component)}"
HOME_PKG="${HOME_COMPONENT%%/*}"
HOME_CLASS="$(component_class "$HOME_COMPONENT")"
# Mostra a tela inicial medida: o launcher indicado (aberto direto) ou o Home do sistema.
show_home() {
    if [ -n "$LAUNCHER" ]; then tv am start -n "$LAUNCHER" > /dev/null; else tv input keyevent KEYCODE_HOME; fi
}

log "Estabilizando: tela inicial e 45 s parado ($HOME_COMPONENT${LAUNCHER:+, aberto direto})"
show_home
sleep 45

# ---- memoria ------------------------------------------------------------------------------------
log "Memoria"
tv cat /proc/meminfo > "$OUT/meminfo.txt"
tv dumpsys -t 120 meminfo > "$OUT/dumpsys_meminfo.txt"
tv ps -A -o PID,RSS,NAME > "$OUT/ps.txt"
MEM_AVAIL=$(awk '/^MemAvailable/ { print int($2 / 1024) }' "$OUT/meminfo.txt")
SWAP_USED=$(awk '/^SwapTotal/ { t = $2 } /^SwapFree/ { f = $2 } END { print int((t - f) / 1024) }' "$OUT/meminfo.txt")
PROCS=$(($(wc -l < "$OUT/ps.txt") - 1))
kb_line() { grep -m1 "$1" "$OUT/dumpsys_meminfo.txt" | sed -E "s/.*$1 *([0-9,]+)K.*/\1/" | tr -d , | awk '{ print int($1 / 1024) }'; }
FREE_RAM=$(kb_line "Free RAM:")
USED_RAM=$(kb_line "Used RAM:")
MEM_STATUS=$(grep -m1 "Total RAM:" "$OUT/dumpsys_meminfo.txt" | sed -E 's/.*status ([a-z]+).*/\1/')

# ---- CPU com a TV parada (60 s, contadores do kernel) --------------------------------------------
log "CPU em repouso (60 s)"
tv dumpsys gfxinfo "$HOME_PKG" reset > /dev/null
cpu_sample 60 > "$OUT/cpu.txt"
CPU_BUSY=$(head -1 "$OUT/cpu.txt")   # % da capacidade total (4 nucleos)
tail -n +2 "$OUT/cpu.txt" > "$OUT/cpu_top.txt"
IDLE_FPS=$(tv dumpsys gfxinfo "$HOME_PKG" | awk -F': ' '/Total frames rendered/ { printf "%.1f", $2 / 60; exit }')

# ---- abertura de apps (a frio: app encerrado antes) ----------------------------------------------
declare -A START_MED
declare -A START_WAIT
for entry in "${APPS[@]}"; do
    IFS='|' read -r name pkg comp <<< "$entry"
    log "Abertura a frio: $name (${N}x)"
    : > "$OUT/start_${name}_ms.txt"
    : > "$OUT/start_${name}_wait_ms.txt"
    for i in $(seq 1 "$N"); do
        tv am force-stop "$pkg"
        sleep 2
        r=$(tv am start -W -n "$comp")
        echo "$r" >> "$OUT/start_${name}.txt"
        echo "$r" | awk -F': ' '/^TotalTime/ { print $2 }' >> "$OUT/start_${name}_ms.txt"
        # WaitTime e o tempo ate o primeiro quadro; TotalTime pode somar uma transicao de
        # activity anterior (ex.: ~3,7 s na sequencia Configuracoes -> Home).
        echo "$r" | awk -F': ' '/^WaitTime/ { print $2 }' >> "$OUT/start_${name}_wait_ms.txt"
        sleep 8
        show_home
        sleep 4
    done
    START_MED[$name]=$(median < "$OUT/start_${name}_ms.txt")        # TotalTime (historico do CSV)
    START_WAIT[$name]=$(median < "$OUT/start_${name}_wait_ms.txt")  # WaitTime (tempo ate o 1o quadro)
done

# ---- volta a tela inicial a partir de um app: tecla Home (ou abertura direta) -> launcher retomado --
log "Volta a tela inicial (${N}x)"
: > "$OUT/home_return_ms.txt"
for i in $(seq 1 "$N"); do
    tv am start -W -n com.android.tv.settings/.MainSettings > /dev/null
    sleep 4
    "$ADB" -s "$SERIAL" logcat -c -b main -b system -b events
    show_home
    sleep 4
    "$ADB" -s "$SERIAL" logcat -d -b main -b system -b events -v epoch | tr -d '\r' |
        awk -v pkg="$HOME_PKG" -v cls="$HOME_CLASS" -v direct="${LAUNCHER:+1}" '
            !direct && /injectKeyEvent: KeyEvent \{ action=ACTION_DOWN, keyCode=KEYCODE_HOME/ && !k { k = $1 }
            direct && /START u0/ && index($0, "cmp=" pkg "/") && !k { k = $1 }
            k && /am_on_resume_called/ && index($0, cls) && !r { r = $1 }
            END { if (k && r) printf "%d\n", (r - k) * 1000 }' >> "$OUT/home_return_ms.txt"
done
HOME_MS=$(median < "$OUT/home_return_ms.txt")

# ---- fluidez da navegacao no Home (quadros atrasados) --------------------------------------------
log "Navegacao no Home (${N}x)"
show_home
sleep 3
tv dumpsys gfxinfo "$HOME_PKG" reset > /dev/null
for i in $(seq 1 "$N"); do
    tv input keyevent 22 22 22 22 22 22 20 22 22 22 22 19 21 21 21 21 21 21 21 21 21 21
    sleep 2
done
tv dumpsys gfxinfo "$HOME_PKG" > "$OUT/gfxinfo_home.txt"
FRAMES=$(grep -m1 "Total frames rendered" "$OUT/gfxinfo_home.txt" | awk -F': ' '{ print $2 }')
JANK_PCT=$(grep -m1 "Janky frames" "$OUT/gfxinfo_home.txt" | sed -E 's/.*\(([0-9.]+)%\).*/\1/')
P90=$(grep -m1 "90th percentile" "$OUT/gfxinfo_home.txt" | awk -F': ' '{ print $2 }' | tr -d 'ms')
P99=$(grep -m1 "99th percentile" "$OUT/gfxinfo_home.txt" | awk -F': ' '{ print $2 }' | tr -d 'ms')

# ---- Assistente: tecla de busca -> microfone aberto -> conexao com o servidor ---------------------
log "Assistente (${N}x)"
: > "$OUT/assist.txt"
for i in $(seq 1 "$N"); do
    "$ADB" -s "$SERIAL" logcat -c -b main -b system -b events
    tv input keyevent KEYCODE_SEARCH
    sleep 8
    "$ADB" -s "$SERIAL" logcat -d -b main -b system -b events -v epoch | tr -d '\r' > "$OUT/assist_$i.log"
    tv input keyevent KEYCODE_BACK
    sleep 4
    awk '
        /injectKeyEvent: KeyEvent \{ action=ACTION_DOWN, keyCode=KEYCODE_SEARCH/ && !k { k = $1 }
        /VoicePlateActivity.*onStart/ && !u { u = $1 }
        /adev_open_input_stream: flags/ && !m { m = $1 }
        /LIBAS_S3_CONNECTION_UP/ && !s { s = $1 }
        END { if (k) printf "%d %d %d\n", (u ? (u - k) * 1000 : -1), (m ? (m - k) * 1000 : -1), (s ? (s - k) * 1000 : -1) }
    ' "$OUT/assist_$i.log" >> "$OUT/assist.txt"
done
ASSIST_UI=$(awk '$1 >= 0 { print $1 }' "$OUT/assist.txt" | median)
ASSIST_MIC=$(awk '$2 >= 0 { print $2 }' "$OUT/assist.txt" | median)
ASSIST_SRV=$(awk '$3 >= 0 { print $3 }' "$OUT/assist.txt" | median)

# ---- inicializacao (so vale logo apos um reboot) --------------------------------------------------
BOOT_MS=$(tv logcat -b events -d | grep -m1 boot_progress_enable_screen | awk -F': ' '{ print $2 }')
UPTIME_S=$(tv cat /proc/uptime | awk '{ print int($1) }')

show_home

cat > "$OUT/resumo.txt" <<EOF
Medicao: $LABEL ($(date '+%Y-%m-%d %H:%M')), repeticoes: $N, Home: $HOME_COMPONENT, uptime: ${UPTIME_S}s
Memoria   MemAvailable ${MEM_AVAIL} MB | Free RAM ${FREE_RAM} MB | Used RAM ${USED_RAM} MB | swap usada ${SWAP_USED} MB | processos ${PROCS} | status ${MEM_STATUS}
CPU       parada: ${CPU_BUSY}% da capacidade total (4 nucleos), tela inicial desenhando ${IDLE_FPS} quadros/s. Maiores consumidores (% de 1 nucleo):
$(sed 's/^/          /' "$OUT/cpu_top.txt")
Abertura  a frio, mediana WaitTime ms: settings ${START_WAIT[settings]} | youtube ${START_WAIT[youtube]} | netflix ${START_WAIT[netflix]} | globoplay ${START_WAIT[globoplay]} | spotify ${START_WAIT[spotify]}
Home      volta ao Home ${HOME_MS} ms | navegacao: ${FRAMES} quadros, ${JANK_PCT}% atrasados, p90 ${P90} ms, p99 ${P99} ms
Voz       busca -> tela ${ASSIST_UI} ms | microfone aberto ${ASSIST_MIC} ms | servidor conectado ${ASSIST_SRV} ms
Boot      boot_progress_enable_screen: ${BOOT_MS:-fora do buffer} ms
EOF
cat "$OUT/resumo.txt"

CSV=medicoes/historico.csv
[ -f "$CSV" ] || echo "data,rotulo,home,mem_avail_mb,free_ram_mb,used_ram_mb,swap_mb,processos,cpu_parada_pct,settings_ms,youtube_ms,netflix_ms,globoplay_ms,spotify_ms,home_ms,quadros,jank_pct,p90_ms,p99_ms,voz_tela_ms,voz_mic_ms,voz_servidor_ms,boot_ms,uptime_s,fps_parado" > "$CSV"
head -1 "$CSV" | grep -q fps_parado || sed -i '1s/$/,fps_parado/' "$CSV"
# Colunas novas de WaitTime (tempo ate o 1o quadro). As colunas *_ms antigas continuam sendo
# TotalTime para nao misturar metodos nas linhas ja gravadas.
for c in settings_wait_ms youtube_wait_ms netflix_wait_ms globoplay_wait_ms spotify_wait_ms; do
    head -1 "$CSV" | grep -q "$c" || sed -i "1s/$/,$c/" "$CSV"
done
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$HOME_PKG,$MEM_AVAIL,$FREE_RAM,$USED_RAM,$SWAP_USED,$PROCS,$CPU_BUSY,${START_MED[settings]},${START_MED[youtube]},${START_MED[netflix]},${START_MED[globoplay]},${START_MED[spotify]},$HOME_MS,$FRAMES,$JANK_PCT,$P90,$P99,$ASSIST_UI,$ASSIST_MIC,$ASSIST_SRV,${BOOT_MS:-},$UPTIME_S,$IDLE_FPS,${START_WAIT[settings]},${START_WAIT[youtube]},${START_WAIT[netflix]},${START_WAIT[globoplay]},${START_WAIT[spotify]}" >> "$CSV"
log "Pronto: $OUT"
