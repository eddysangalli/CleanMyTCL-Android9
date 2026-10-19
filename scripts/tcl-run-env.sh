#!/usr/bin/env bash
# Registra o estado do ambiente de uma bateria de testes, para que os numeros republished
# sao sempre legiveis no contexto em que foram medidos.
#
# Uso: scripts/tcl-run-env.sh <pasta-de-saida> [rotulo]
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/tcl-lib.sh
tv_connect

OUT="${1:?uso: tcl-run-env.sh <pasta> [rotulo]}"
LABEL="${2:-run}"
mkdir -p "$OUT"

{
  echo "rotulo=$LABEL"
  echo "data_hora=$(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "host=$(hostname)"
  echo
  echo "# codigo"
  echo "repo=$(pwd)"
  echo "commit=$(git rev-parse HEAD 2>/dev/null)"
  echo "commit_curto=$(git rev-parse --short HEAD 2>/dev/null)"
  echo "branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
  echo "descricao=$(git log -1 --format=%s 2>/dev/null)"
  echo "arvore_limpa=$([ -z "$(git status --porcelain 2>/dev/null)" ] && echo sim || echo nao)"
  echo
  echo "# tv"
  echo "android_release=$(tv getprop ro.build.version.release)"
  echo "android_sdk=$(tv getprop ro.build.version.sdk)"
  echo "fingerprint=$(tv getprop ro.build.fingerprint)"
  echo "display_id=$(tv getprop ro.build.display.id)"
  echo "security_patch=$(tv getprop ro.build.version.security_patch)"
  echo "model=$(tv getprop ro.product.model)"
  echo "brand=$(tv getprop ro.product.brand)"
  echo "board=$(tv getprop ro.product.board)"
  echo "platform=$(tv getprop ro.board.platform)"
  echo "abi=$(tv getprop ro.product.cpu.abi)"
  echo "uptime_s=$(tv cat /proc/uptime | cut -d' ' -f1)"
  echo
  echo "# home"
  echo "home=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | tail -1)"
  echo "launcher_pkg=br.eddy.tvhome"
  echo "launcher_version=$(tv dumpsys package br.eddy.tvhome | grep -m1 -oE 'versionName=[^ ]+' | sed 's/versionName=//')"
  # A linha e "versionCode=13 minSdk=26 targetSdk=29": um sed guloso pegaria o 29 do targetSdk.
  echo "launcher_versioncode=$(tv dumpsys package br.eddy.tvhome | grep -m1 -oE 'versionCode=[0-9]+' | sed 's/versionCode=//')"
  echo
  echo "# pacotes"
  echo "pacotes_total=$(tv pm list packages | wc -l)"
  echo "pacotes_desativados=$(tv pm list packages -d | wc -l)"
  echo "pacotes_terceiros=$(tv pm list packages -3 | wc -l)"
  echo
  echo "# memoria"
  tv cat /proc/meminfo | grep -E 'MemTotal|MemFree|MemAvailable|Buffers|Cached|SwapTotal|SwapFree' | sed 's/^/meminfo_/'
  echo
  echo "# tela"
  echo "screen_off_timeout_ms=$(tv settings get system screen_off_timeout)"
  echo "animator_duration_scale=$(tv settings get global animator_duration_scale)"
  echo "transition_animation_scale=$(tv settings get global transition_animation_scale)"
  echo "window_animation_scale=$(tv settings get global window_animation_scale)"
  echo "acessibilidade=$(tv settings get secure enabled_accessibility_services)"
  echo
  echo "# rede"
  echo "ethernet=$(tv dumpsys connectivity | grep -c 'NetworkAgentInfo.*ETHERNET')"
  echo "wifi_state=$(tv cmd wifi status 2>/dev/null | head -1)"
  echo "dns_ok=$(tv ping -c1 -W2 8.8.8.8 >/dev/null 2>&1 && echo sim || echo nao)"
  echo
  echo "# temperatura (nem sempre existe)"
  for z in /sys/class/thermal/thermal_zone0/temp /sys/class/power_supply/thermal/temp /sys/devices/virtual/thermal/thermal_zone0/temp; do
    v=$(tv cat $z 2>/dev/null | tr -d '\r')
    if [ -n "$v" ] && [ "$v" != "cat: No such file" ]; then
      echo "thermal_$(basename $(dirname $z))_$(basename $z)=$v"
    fi
  done
  echo
  echo "# versoes dos aplicativos testados"
  for p in com.netflix.ninja com.google.android.youtube.tv com.globo.globotv com.spotify.tv.android; do
    ver=$(tv dumpsys package $p 2>/dev/null | grep -m1 versionName | sed 's/.*=//')
    st=$(tv dumpsys package $p 2>/dev/null | grep -m1 'User 0:' | grep -o 'enabled=[0-9]*')
    echo "app_$p=${ver:-ausente} ${st:-}"
  done
} > "$OUT/ambiente.txt" 2>&1

cat "$OUT/ambiente.txt"
