#!/usr/bin/env bash
# Auditoria somente leitura: o que mais consome ou acorda a TV, estado de atualizacao, CPU/temperatura,
# armazenamento e versoes dos apps principais. Nao altera nada.
#
# Uso: scripts/tcl-audit.sh
# Saida: auditoria/<data-hora>/ (dados brutos) e resumo.txt
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
OUT="auditoria/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
tv_connect

log "Coletando em $OUT"
tv getprop > "$OUT/getprop.txt"
tv 'ls -la /tclconfig 2>&1; for f in /tclconfig/*; do [ -f "$f" ] && { echo "== $f"; head -c 2000 "$f" 2>&1; echo; }; done' > "$OUT/tclconfig.txt"
tv dumpsys alarm > "$OUT/alarm.txt"
tv dumpsys jobscheduler > "$OUT/jobscheduler.txt"
tv dumpsys diskstats > "$OUT/diskstats.txt"
tv dumpsys power > "$OUT/power.txt"
tv dumpsys activity services > "$OUT/services.txt"
tv dumpsys activity processes > "$OUT/processes.txt"
tv 'for c in 0 1 2 3; do d=/sys/devices/system/cpu/cpu$c/cpufreq; echo "cpu$c gov=$(cat $d/scaling_governor 2>&1) cur=$(cat $d/scaling_cur_freq 2>&1) min=$(cat $d/scaling_min_freq 2>&1) max=$(cat $d/scaling_max_freq 2>&1)"; done; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies 2>&1; for z in /sys/class/thermal/thermal_zone*; do echo "$z $(cat $z/type 2>&1) $(cat $z/temp 2>&1)"; done' > "$OUT/cpu_thermal.txt"
tv 'cat /proc/uptime; for p in /proc/[0-9]*; do s=$(cat $p/stat 2>/dev/null) || continue; echo "$s"; done' > "$OUT/proc_stat.txt"
for k in hdmi_control_enabled hdmi_control_auto_wakeup_enabled hdmi_control_auto_device_off_enabled stay_on_while_plugged_in window_animation_scale transition_animation_scale animator_duration_scale always_finish_activities; do
    echo "global/$k=$(tv settings get global $k)"
done > "$OUT/settings.txt"
for k in screensaver_enabled screensaver_components sleep_timeout; do echo "secure/$k=$(tv settings get secure $k)"; done >> "$OUT/settings.txt"
echo "system/screen_off_timeout=$(tv settings get system screen_off_timeout)" >> "$OUT/settings.txt"
for p in com.netflix.ninja com.google.android.youtube.tv com.globo.globotv com.spotify.tv.android com.google.android.webview \
         com.google.android.gms com.android.vending com.google.android.katniss com.google.android.apps.mediashell \
         com.tcl.versionUpdateApp br.eddy.tvhome; do
    echo "$p $(tv dumpsys package $p | grep -m2 -E 'versionName|lastUpdateTime' | sed 's/^ *//' | tr '\n' ' ')"
done > "$OUT/versions.txt"
"$ADB" -s "$SERIAL" logcat -d -b main -b system -v time | tr -d '\r' | grep -iE "versionupdate|UpdateService|ota|upgrade" | tail -40 > "$OUT/update_log.txt"

# CPU acumulada desde que cada processo nasceu (ticks de 10 ms), os 15 maiores
UP=$(head -1 "$OUT/proc_stat.txt" | cut -d' ' -f1)
awk -v up="$UP" 'NR > 1 {
    o = index($0, "("); c = 0; for (i = length($0); i > 0; i--) if (substr($0, i, 1) == ")") { c = i; break }
    if (!o || !c) next
    name = substr($0, o + 1, c - o - 1); split(substr($0, c + 2), f, " ")
    cpu = (f[12] + f[13]) / 100; age = up - f[20] / 100
    if (cpu > 1) printf "%8.0f s CPU | %5.1f%% de 1 nucleo em media | %6.0f s de vida | %s\n", cpu, (age > 0 ? 100 * cpu / age : 0), age, name
}' "$OUT/proc_stat.txt" | sort -rn | head -15 > "$OUT/cpu_acumulada.txt"

# Alarmes que mais acordam / disparam (Top Alarms) e jobs por pacote
sed -n '/Top Alarms:/,/^$/p' "$OUT/alarm.txt" | head -25 > "$OUT/top_alarms.txt"
grep -oE "JOB #[^ ]+ [0-9a-f]+ [^ ]+" "$OUT/jobscheduler.txt" | awk '{ print $4 }' | sed 's|/.*||' | sort | uniq -c | sort -rn | head -15 > "$OUT/jobs_por_pacote.txt"

{
    echo "== Uptime: ${UP}s"
    echo "== CPU acumulada por processo"; cat "$OUT/cpu_acumulada.txt"
    echo "== Alarmes mais frequentes"; cat "$OUT/top_alarms.txt"
    echo "== Jobs agendados por pacote"; cat "$OUT/jobs_por_pacote.txt"
    echo "== CPU e temperatura"; cat "$OUT/cpu_thermal.txt"
    echo "== Armazenamento (diskstats)"; grep -E "Latency|Recent Disk Write Speed|Data-Free|System-Free|Cache-Free" "$OUT/diskstats.txt"
    echo "== Configuracoes"; cat "$OUT/settings.txt"
    echo "== Versoes"; cat "$OUT/versions.txt"
    echo "== Atualizacao (log)"; tail -10 "$OUT/update_log.txt"
} > "$OUT/resumo.txt"
cat "$OUT/resumo.txt"
log "Pronto: $OUT"
