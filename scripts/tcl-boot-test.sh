#!/usr/bin/env bash
# Reinicia a TV (adb reboot: reinicio quente, nao e o mesmo que tirar da tomada) e mede:
#  - comando -> ADB de volta -> boot_completed -> Home em foco (relogio do PC)
#  - marcos internos do Android (boot_progress_*, Boot is finished, Displayed do Home)
#  - CPU total a cada 5 s depois que o Home aparece (a "tempestade" de processos do boot)
#  - opcional: abre um app N segundos depois do Home e registra ate ficar utilizavel
# Recusa reiniciar se o ADB por rede nao for persistente.
#
# Uso: scripts/tcl-boot-test.sh <rotulo> [app=netflix|youtube|globoplay|nenhum] [espera_s=8] [cpu_s=180]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
LABEL="${1:?uso: tcl-boot-test.sh <rotulo> [app] [espera_s] [cpu_s]}"
APP="${2:-nenhum}"
WAIT_S="${3:-8}"
CPU_S="${4:-180}"
OUT="investigacao/netflix-boot/$(date +%Y%m%d-%H%M%S)-boot-$LABEL"
mkdir -p "$OUT"
tv_connect
[ "$(tv getprop persist.adb.tcp.port)" = 5555 ] || { echo "ADB por rede nao persistente; abortado"; exit 3; }

HOME_PKG=$(tv_home_component); HOME_PKG="${HOME_PKG%%/*}"
log "Reiniciando ($LABEL)"
T0=$(date +%s)
"$ADB" -s "$SERIAL" reboot
sleep 15
until "$ADB" connect "$SERIAL" 2>/dev/null | grep -q "connected to"; do sleep 1; done
T_ADB=$(date +%s)
until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 1; done
T_BC=$(date +%s)
until [ "$(tv_focus_pkg)" = "$HOME_PKG" ]; do sleep 1; done
T_HOME=$(date +%s)
log "ADB +$((T_ADB - T0))s | boot_completed +$((T_BC - T0))s | Home em foco +$((T_HOME - T0))s"

"$ADB" -s "$SERIAL" logcat -d -b events | tr -d '\r' | grep -E "boot_progress|sf_stop_bootanim|am_activity_launch_time" > "$OUT/boot_events.txt"
"$ADB" -s "$SERIAL" logcat -d -b main -b system | tr -d '\r' | grep -E "Boot is finished|Displayed $HOME_PKG|System now ready" > "$OUT/boot_main.txt"
tv getprop | grep -E "ro.boottime|sys.boot.reason|bootreason" > "$OUT/boottime_props.txt"

# CPU depois do boot; se houver app, abre-o apos WAIT_S segundos e o resto da CPU vem do teste do app
echo "t_desde_home_s,cpu_total_pct" > "$OUT/cpu_pos_boot.csv"
LIMIT=$([ "$APP" = nenhum ] && echo "$CPU_S" || echo "$WAIT_S")
prev=$(tv head -1 /proc/stat)
while [ $(( $(date +%s) - T_HOME )) -lt "$LIMIT" ]; do
    sleep 5
    cur=$(tv head -1 /proc/stat)
    printf '%s\n%s\n' "$prev" "$cur" | awk -v t="$(( $(date +%s) - T_HOME ))" '
        { tot = 0; for (i = 2; i <= NF; i++) tot += $i; idle = $5 + $6
          if (NR == 1) { T1 = tot; I1 = idle } else { T2 = tot; I2 = idle } }
        END { printf "%s,%.1f\n", t, 100 * (1 - (I2 - I1) / (T2 - T1)) }' >> "$OUT/cpu_pos_boot.csv"
    prev="$cur"
done

{
    echo "rotulo=$LABEL app=$APP espera_apos_home_s=$WAIT_S"
    echo "adb_s=$((T_ADB - T0)) boot_completed_s=$((T_BC - T0)) home_em_foco_s=$((T_HOME - T0))"
    cat "$OUT/boot_events.txt" "$OUT/boot_main.txt" "$OUT/boottime_props.txt"
} > "$OUT/resumo.txt"
echo "$(date '+%Y-%m-%d %H:%M'),$LABEL,$((T_ADB - T0)),$((T_BC - T0)),$((T_HOME - T0))" >> investigacao/netflix-boot/boots.csv

if [ "$APP" != nenhum ]; then
    bash scripts/tcl-app-start-test.sh "$APP" "pos-boot-$LABEL" 150 frio
fi
log "Pronto: $OUT"
