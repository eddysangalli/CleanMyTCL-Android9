# Funcoes compartilhadas pelos scripts bash da TCL (Git Bash no Windows). Use: . "$(dirname "$0")/tcl-lib.sh"
export MSYS_NO_PATHCONV=1

if [ -z "${ADB:-}" ]; then
    if command -v adb >/dev/null 2>&1; then
        ADB=adb
    else
        ADB="$(cygpath -u "$LOCALAPPDATA")/Microsoft/WinGet/Packages/Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe/platform-tools/adb.exe"
    fi
fi
TV_HOST="${TCL_TV_HOST:?Set TCL_TV_HOST=<tv-ip> (defina o IP da TV)}"
SERIAL="$TV_HOST:5555"

# stdin de /dev/null: o adb shell le a entrada padrao e engoliria o resto de um "while read".
tv() { "$ADB" -s "$SERIAL" shell "$@" < /dev/null | tr -d '\r'; }
log() { echo "[$(date +%H:%M:%S)] $*"; }

tv_connect() {
    "$ADB" connect "$SERIAL" >/dev/null 2>&1
    [ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null | tr -d '\r')" = device ] || { echo "TV $SERIAL indisponivel"; exit 2; }
}

# Componente que hoje responde ao Home (ex.: com.google.android.tvlauncher/.MainActivity).
tv_home_component() {
    tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1
}

# Pacote da janela em foco.
tv_focus_pkg() {
    tv dumpsys window | grep -m1 mCurrentFocus | sed -E 's/.* u0 ([^/ ]+)\/.*/\1/'
}

# Entradas HDMI/AV com dispositivo conectado (estado 0), uma por linha.
tv_connected_passthrough() {
    tv dumpsys tv_input | grep -E "passthroughinput.*state: 0" | sed -E 's/^ *([^:]+): info.*/\1/'
}

# URI que o app de TV abre para uma entrada (igual a TvContract.buildChannelUriForPassthroughInput).
passthrough_uri() {
    echo "content://android.media.tv/passthrough/$(echo "$1" | sed 's|/|%2F|g')"
}

# Amostra a CPU por N segundos pelos contadores do kernel (/proc/stat e /proc/<pid>/stat).
# 1a linha: % ocupada da capacidade total (4 nucleos). Depois: os 12 maiores processos em % de 1 nucleo.
cpu_sample() {
    tv "head -1 /proc/stat; cat /proc/[0-9]*/stat 2>/dev/null; sleep $1; echo ===; head -1 /proc/stat; cat /proc/[0-9]*/stat 2>/dev/null" |
    awk -v secs="$1" '
        /^===/ { phase = 2; next }
        /^cpu / { tot = 0; for (i = 2; i <= NF; i++) tot += $i; idle = $5 + $6
                  if (phase == 2) { T2 = tot; I2 = idle } else { T1 = tot; I1 = idle }; next }
        {   o = index($0, "("); c = 0; for (i = length($0); i > 0; i--) if (substr($0, i, 1) == ")") { c = i; break }
            if (!o || !c) next
            pid = substr($0, 1, o - 2); name = substr($0, o + 1, c - o - 1)
            split(substr($0, c + 2), f, " "); t = f[12] + f[13]
            if (phase == 2) { if (pid in a) { d[pid] = t - a[pid]; n[pid] = name } } else a[pid] = t }
        END {
            printf "%.1f\n", 100 * (1 - (I2 - I1) / (T2 - T1)); fflush()
            for (p in d) if (d[p] > 0) printf "%6.1f %s\n", d[p] / secs, n[p] | "sort -rn | head -12"
            close("sort -rn | head -12")
        }'
}

# Classe completa da atividade de um componente (pkg/.Classe ou pkg/classe.Completa), como aparece em am_on_resume_called.
component_class() {
    local pkg="${1%%/*}" cls="${1#*/}"
    case "$cls" in .*) echo "$pkg$cls" ;; *) echo "$cls" ;; esac
}

median() {
    sort -n | awk '{a[NR]=$1} END { if (NR == 0) print "NA"; else if (NR % 2) print a[(NR + 1) / 2]; else print int((a[NR / 2] + a[NR / 2 + 1]) / 2) }'
}
