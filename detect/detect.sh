#!/usr/bin/env bash
# detect.sh - prints your CPU, GPU, RAM, free disk, distro, kernel and Vulkan support as JSON.
# It only reads: lscpu, lspci, nvidia-smi, glxinfo, vulkaninfo, /proc/meminfo, /etc/os-release,
# /sys/bus/pci/devices/<id>/mem_info_vram_total, df of your home folder and uname. It never reads your hostname, user name, IP, MAC address,
# serial numbers or machine-id. It does not send anything: the JSON goes to your terminal only.
set -euo pipefail
export LC_ALL=C # lscpu output is translated otherwise

PCI_SYS=/sys/bus/pci/devices # tests point this at a fake folder after they source this file

have() { command -v "$1" >/dev/null 2>&1; }
# glxinfo and vulkaninfo can hang without a display, and nvidia-smi with a broken driver, so give them 5 seconds
run() { if have timeout; then timeout 5 "$@"; else "$@"; fi; }

# json_str VALUE: JSON string literal, or null when VALUE is empty
json_str() {
  local s=$1 i ch esc
  if [[ -z $s ]]; then echo null; return; fi
  s=${s//\\/\\\\}; s=${s//\"/\\\"}
  s=${s//$'\n'/\\n}; s=${s//$'\t'/\\t}; s=${s//$'\r'/\\r}
  for i in {1..31}; do
    printf -v ch %b "\\x$(printf %02x "$i")"; printf -v esc '\\u%04x' "$i"
    s=${s//"$ch"/$esc}
  done
  printf '"%s"\n' "$s"
}

# json_num VALUE: VALUE if it is a non-negative integer, else null
json_num() { if [[ $1 =~ ^[0-9]+$ ]]; then echo "$1"; else echo null; fi; }

lscpu_get() { awk -F: -v k="$1" '$1 == k { sub(/^[^:]*: */, ""); print; exit }' <<<"$LSCPU"; }
os_get() { sed -n "s/^$1=//p" /etc/os-release 2>/dev/null | head -1 | tr -d "\"'" || true; }
# vk_gpu: reads vulkaninfo --summary on stdin. Prints "gpu <apiVersion>" for the first device
# that is not a CPU (lavapipe/llvmpipe). Prints nothing when there is no such device.
vk_gpu() {
  awk '
    function flush() {
      if (seen && type !~ /PHYSICAL_DEVICE_TYPE_CPU/) { print "gpu " api; found = 1; exit }
      seen = 0; api = ""; type = ""
    }
    /^GPU[0-9]+:/ { flush(); seen = 1; next }
    seen && /apiVersion/ && match($0, /[0-9]+\.[0-9]+(\.[0-9]+)?/) { api = substr($0, RSTART, RLENGTH) }
    seen && /deviceType/ { type = $0 }
    END { if (!found) flush() }'
}
mesa_from() { sed -n 's/.*Mesa \([0-9][0-9.]*[0-9]\).*/\1/p' | head -1 || true; }

# Sets MESA (e.g. "24.0.5") once. Called only when an AMD or Intel GPU exists.
load_mesa() {
  [[ -z ${MESA_DONE:-} ]] || return 0
  MESA_DONE=1 MESA=""
  if have glxinfo; then MESA=$(run glxinfo -B 2>/dev/null | grep '^OpenGL version string' | mesa_from || true); fi
  if [[ -z $MESA ]]; then MESA=$(grep driverInfo <<<"$VK" | mesa_from || true); fi
}

main() {
  local cpu_model cpu_cores cpu_threads cps sockets ram disk
  LSCPU=""; VK=""; MESA=""
  have lscpu && LSCPU=$(lscpu 2>/dev/null || true)
  cpu_model=$(lscpu_get "Model name")
  cpu_threads=$(lscpu_get "CPU(s)") # exact key: "CPU(s) scaling MHz" also exists
  cps=$(lscpu_get "Core(s) per socket"); sockets=$(lscpu_get "Socket(s)")
  cpu_cores=""
  if [[ $cps =~ ^[0-9]+$ && $sockets =~ ^[0-9]+$ ]]; then cpu_cores=$((cps * sockets)); fi

  ram=$(awk '/^MemTotal:/ { print int($2 / 1024) }' /proc/meminfo 2>/dev/null || true)
  disk=$(df -Pk "${HOME:-.}" 2>/dev/null | awk 'NR == 2 { print int($4 / 1024) }' || true)

  # Vulkan: no tool or timeout (124) -> unknown (null), tool fails or finds only a CPU device -> false
  local vk_supported=null vk_api="" rc=0 vk_dev
  if have vulkaninfo; then
    VK=$(run vulkaninfo --summary 2>/dev/null) || rc=$?
    vk_dev=$(vk_gpu <<<"$VK")
    if ((rc == 0)) && [[ -n $vk_dev ]]; then vk_supported=true; vk_api=${vk_dev#gpu }
    elif ((rc != 124)); then vk_supported=false; fi
  fi

  # GPUs, one entry per index in these arrays
  local gv=() gm=() gr=() gd=() gb=() bus class vendor device vid dev v i f b
  if have lspci; then
    while IFS='"' read -r bus class _ vendor _ device _; do
      case $class in *"[0300]" | *"[0302]" | *"[0380]") ;; *) continue ;; esac
      vid=${vendor##*\[}; vid=${vid%\]}
      case ${vid,,} in 10de) v=nvidia ;; 1002) v=amd ;; 8086) v=intel ;; *) v=other ;; esac
      dev=${device% \[*\]} # drop the trailing " [device id]"
      if [[ $dev =~ \[(.*)\] ]]; then dev=${BASH_REMATCH[1]}; fi
      if [[ $dev == Device ]]; then dev=""; fi # name unknown to pci.ids
      bus=${bus% }
      gv+=("$v"); gm+=("$dev"); gr+=(""); gd+=(""); gb+=("${bus,,}")
    done < <(lspci -mm -nn -D 2>/dev/null || true)
  fi

  # NVIDIA: nvidia-smi knows the exact name, VRAM and driver. Match by PCI bus id.
  if have nvidia-smi; then
    local nb name mem drv
    while IFS=, read -r nb name mem drv; do
      nb=${nb//[[:space:]]/}; nb=${nb,,}
      [[ $nb =~ ^[0-9a-f]+:[0-9a-f]{2}:[0-9a-f]{2}\.[0-9a-f]$ ]] || continue # skip error text from a broken driver
      nb=${nb#0000} # nvidia-smi uses an 8-digit PCI domain, lspci uses 4
      i=-1
      for f in "${!gb[@]}"; do if [[ ${gb[f]} == "$nb" ]]; then i=$f; break; fi; done
      if ((i < 0)); then i=${#gb[@]}; gv[i]=nvidia; gm[i]=""; gd[i]=""; gb[i]=$nb; fi
      name=${name# }; drv=${drv// /}
      # nvidia-smi prints [N/A] or [Not Supported] for unknown values
      if [[ -n $name && $name != \[*\] ]]; then gm[i]=$name; fi
      if [[ -n $drv && $drv != \[*\] ]]; then gd[i]="nvidia $drv"; fi
      gr[i]=${mem// /}
    done < <(run nvidia-smi --query-gpu=pci.bus_id,name,memory.total,driver_version \
      --format=csv,noheader,nounits 2>/dev/null || true)
  fi

  # AMD/Intel: VRAM from sysfs, driver is the system-wide Mesa version
  for i in "${!gv[@]}"; do
    case ${gv[i]} in
      amd | intel)
        f=$PCI_SYS/${gb[i]}/mem_info_vram_total
        b=$(cat "$f" 2>/dev/null || true)
        if [[ $b =~ ^[0-9]+$ ]]; then gr[i]=$((b / 1048576)); fi
        load_mesa
        if [[ -n $MESA ]]; then gd[i]="mesa $MESA"; fi
        ;;
    esac
  done

  local gpus="[]" sep=""
  if ((${#gv[@]} > 0)); then
    gpus="["
    for i in "${!gv[@]}"; do
      gpus+=$(printf '%s\n    {\n      "vendor": "%s",\n      "model": %s,\n      "vramMb": %s,\n      "driver": %s\n    }' \
        "$sep" "${gv[i]}" "$(json_str "${gm[i]}")" "$(json_num "${gr[i]}")" "$(json_str "${gd[i]}")")
      sep=","
    done
    gpus+=$'\n  ]'
  fi

  printf '{\n  "schemaVersion": 1,\n'
  printf '  "cpu": {\n    "model": %s,\n    "cores": %s,\n    "threads": %s\n  },\n' \
    "$(json_str "$cpu_model")" "$(json_num "$cpu_cores")" "$(json_num "$cpu_threads")"
  printf '  "gpus": %s,\n  "ramMb": %s,\n  "diskFreeMb": %s,\n' "$gpus" "$(json_num "$ram")" "$(json_num "$disk")"
  printf '  "distro": {\n    "id": %s,\n    "name": %s,\n    "version": %s\n  },\n' \
    "$(json_str "$(os_get ID)")" "$(json_str "$(os_get NAME)")" "$(json_str "$(os_get VERSION_ID)")"
  printf '  "kernel": %s,\n' "$(json_str "$(uname -r 2>/dev/null || true)")"
  printf '  "vulkan": {\n    "supported": %s,\n    "apiVersion": %s\n  }\n}\n' "$vk_supported" "$(json_str "$vk_api")"
}

# run only when executed, not when sourced (tests source this file)
if [[ ${BASH_SOURCE[0]:-$0} == "$0" ]]; then main "$@"; fi
