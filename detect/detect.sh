#!/usr/bin/env bash
# detect.sh - prints your CPU, GPUs, RAM, free disk, distro, kernel and Vulkan support as JSON.
# It reads: lscpu, lspci, nvidia-smi, glxinfo, vulkaninfo, /proc/meminfo, /etc/os-release,
#   df of your home folder, uname -r, and /sys/bus/pci/devices/*/mem_info_vram_total.
#   PCI addresses are only used to match GPUs between tools. They are never printed.
# It never reads: your hostname, user name, IP address, MAC address, serial numbers or machine-id.
# It makes no network calls and sends nothing. The JSON goes to your terminal only.
set -euo pipefail
export LC_ALL=C # tool output is translated otherwise

PCI_SYS=${PCI_SYS:-/sys/bus/pci/devices} # tests point this at a fake folder
# Separates the fields of one GPU line. Unlike a tab, read keeps empty fields around it.
FIELD_SEP=$'\x1f'

have() { command -v "$1" >/dev/null 2>&1; }
# nvidia-smi, glxinfo and vulkaninfo can hang on a broken driver or without a display
run() { if have timeout; then timeout 5 "$@"; else "$@"; fi; }

# json_str VALUE: JSON string literal, or null when only whitespace is left after cleanup
json_str() {
  local s=$1
  s=${s//[[:cntrl:]]/ }  # control characters become spaces
  s=${s//[^[:print:]]/}  # drop everything that is not printable ASCII, so the JSON is always valid
  s=${s//\\/\\\\}; s=${s//\"/\\\"}
  if [[ $s =~ ^[[:space:]]*$ ]]; then echo null; else printf '"%s"\n' "$s"; fi
}

# json_num VALUE: VALUE if it is a non-negative integer, else null
json_num() { if [[ $1 =~ ^[0-9]+$ ]]; then echo "$1"; else echo null; fi; }

# ---- CPU ----

# lscpu_field KEY: value of one "Key: value" line of lscpu, which is read from stdin
lscpu_field() { awk -F: -v key="$1" '$1 == key { sub(/^[^:]*: */, ""); print; exit }'; }

cpu_json() {
  local out="" model threads cores_per_socket sockets cores=""
  if have lscpu; then out=$(lscpu 2>/dev/null || true); fi
  model=$(lscpu_field "Model name" <<<"$out")
  threads=$(lscpu_field "CPU(s)" <<<"$out") # exact key: "CPU(s) scaling MHz" also exists
  cores_per_socket=$(lscpu_field "Core(s) per socket" <<<"$out")
  sockets=$(lscpu_field "Socket(s)" <<<"$out")
  if [[ $cores_per_socket =~ ^[0-9]+$ && $sockets =~ ^[0-9]+$ ]]; then
    cores=$((cores_per_socket * sockets))
  fi
  printf '{\n    "model": %s,\n    "cores": %s,\n    "threads": %s\n  }\n' \
    "$(json_str "$model")" "$(json_num "$cores")" "$(json_num "$threads")"
}

# ---- Vulkan (run once: the GPU section also uses it as a fallback for the Mesa version) ----

VULKAN_OUT=""
VULKAN_STATUS=missing # "missing", or the exit code of vulkaninfo

scan_vulkan() {
  if have vulkaninfo; then
    VULKAN_STATUS=0
    VULKAN_OUT=$(run vulkaninfo --summary 2>/dev/null) || VULKAN_STATUS=$?
  fi
}

# Prints "found <apiVersion>" for the first device that is not a CPU, and nothing when there is none.
# A CPU device is the lavapipe/llvmpipe software renderer. The apiVersion may be empty if it cannot be parsed.
# In every device block, apiVersion comes before deviceType. The version is the first dotted number on the
# line, which covers "1.3.280", "1.3.255 (4206847)" and the older "4206802 (1.3.274)".
first_real_gpu() {
  awk '
    /^GPU[0-9]+:/ { api = "" }
    /apiVersion/ && match($0, /[0-9]+\.[0-9]+(\.[0-9]+)?/) { api = substr($0, RSTART, RLENGTH) }
    /deviceType/ && $0 !~ /DEVICE_TYPE_CPU/ { print "found " api; exit }
  ' <<<"$VULKAN_OUT"
}

vulkan_json() {
  local supported=null api="" gpu
  case $VULKAN_STATUS in
    missing | 124) ;; # no tool, or it timed out: we do not know
    0)
      gpu=$(first_real_gpu)
      if [[ -n $gpu ]]; then supported=true; api=${gpu#found }; else supported=false; fi
      ;;
    *) supported=false ;;
  esac
  printf '{\n    "supported": %s,\n    "apiVersion": %s\n  }\n' "$supported" "$(json_str "$api")"
}

# ---- GPUs ----

# Keeps only the dotted version of "... Mesa 24.0.5-1ubuntu1" lines on stdin
mesa_version_from_stdin() { sed -n 's/.*Mesa \([0-9][0-9.]*[0-9]\).*/\1/p' | head -1; }

mesa_version() {
  local version=""
  if have glxinfo; then
    version=$(run glxinfo -B 2>/dev/null | grep '^OpenGL version string' | mesa_version_from_stdin || true)
  fi
  if [[ -z $version ]]; then
    version=$(grep driverInfo <<<"$VULKAN_OUT" | mesa_version_from_stdin || true)
  fi
  echo "$version"
}

# VRAM in MB of an AMD or Intel card. Prints nothing when the kernel does not tell.
sysfs_vram_mb() {
  local bytes
  bytes=$(cat "$PCI_SYS/$1/mem_info_vram_total" 2>/dev/null || true)
  if [[ $bytes =~ ^[0-9]+$ ]]; then echo $((bytes / 1048576)); fi
}

# nvidia-smi values that are unknown come out empty. It prints empty or [N/A] for them.
known() {
  local value=${1# }
  if [[ -z $value || $value == \[*\] ]]; then return 0; fi
  printf '%s' "$value"
}

# One line per NVIDIA GPU: bus id, name, vram_mb, driver version (joined with FIELD_SEP).
nvidia_smi_rows() {
  have nvidia-smi || return 0
  local bus name vram driver
  while IFS=, read -r bus name vram driver; do
    bus=${bus//[[:space:]]/}; bus=${bus,,}
    # A broken driver prints an error message on stdout. Real rows start with a PCI bus id.
    [[ $bus =~ ^[0-9a-f]+:[0-9a-f]{2}:[0-9a-f]{2}\.[0-7]$ ]] || continue
    # nvidia-smi pads the PCI domain to 8 digits, lspci to 4: strip leading zeros down to 4 digits
    if [[ $bus =~ ^0*([0-9a-f]{4,}:.*)$ ]]; then bus=${BASH_REMATCH[1]}; fi
    printf '%s\n' "$bus$FIELD_SEP$(known "$name")$FIELD_SEP$(known "$vram")$FIELD_SEP$(known "$driver")"
  done < <(run nvidia-smi --query-gpu=pci.bus_id,name,memory.total,driver_version \
    --format=csv,noheader,nounits 2>/dev/null || true)
}

# One line per graphics card (VGA, 3D and display controllers): bus id, vendor, model.
lspci_rows() {
  have lspci || return 0
  local bus class vendor_field model_field vendor_id vendor model
  # lspci -mm quotes every field: bus "class [id]" "vendor [id]" "device [id]" ...
  while IFS='"' read -r bus class _ vendor_field _ model_field _; do
    case $class in *"[0300]" | *"[0302]" | *"[0380]") ;; *) continue ;; esac
    vendor_id=${vendor_field##*\[}; vendor_id=${vendor_id%\]}
    case ${vendor_id,,} in
      10de) vendor=nvidia ;; 1002) vendor=amd ;; 8086) vendor=intel ;; *) vendor=other ;;
    esac
    model=${model_field% \[*\]} # drop the trailing " [device id]"
    if [[ $model =~ \[([^]]*)\] ]]; then model=${BASH_REMATCH[1]}; fi # marketing name sits in the first [...]
    if [[ $model == Device ]]; then model=""; fi # unknown to pci.ids
    bus=${bus% }
    printf '%s\n' "${bus,,}$FIELD_SEP$vendor$FIELD_SEP$model"
  done < <(lspci -mm -nn -D 2>/dev/null || true)
}

# One line per GPU: vendor, model, vram_mb, driver (joined with FIELD_SEP). An empty field means unknown.
list_gpus() {
  local -A nvidia_smi=() # bus id -> "name FIELD_SEP vram FIELD_SEP driver"
  local -A listed=()     # bus ids that lspci showed
  local bus vendor model vram driver smi_name smi_driver rest mesa="" smi_rows pci_rows

  smi_rows=$(nvidia_smi_rows)
  pci_rows=$(lspci_rows)
  while IFS=$FIELD_SEP read -r bus rest; do
    if [[ -n $bus ]]; then nvidia_smi[$bus]=$rest; fi
  done <<<"$smi_rows"
  # Looked up once, and only when an AMD or Intel card exists
  if [[ $pci_rows == *"${FIELD_SEP}amd${FIELD_SEP}"* || $pci_rows == *"${FIELD_SEP}intel${FIELD_SEP}"* ]]; then
    mesa=$(mesa_version)
  fi

  while IFS=$FIELD_SEP read -r bus vendor model; do
    [[ -n $bus ]] || continue
    listed[$bus]=1
    vram="" driver=""
    case $vendor in
      nvidia)
        IFS=$FIELD_SEP read -r smi_name vram smi_driver <<<"${nvidia_smi[$bus]-}"
        if [[ -n $smi_name ]]; then model=$smi_name; fi
        if [[ -n $smi_driver ]]; then driver="nvidia $smi_driver"; fi
        ;;
      amd | intel)
        vram=$(sysfs_vram_mb "$bus")
        if [[ -n $mesa ]]; then driver="mesa $mesa"; fi
        ;;
    esac
    printf '%s\n' "$vendor$FIELD_SEP$model$FIELD_SEP$vram$FIELD_SEP$driver"
  done <<<"$pci_rows"

  # nvidia-smi cards that lspci did not list (for example, lspci is missing)
  while IFS=$FIELD_SEP read -r bus smi_name vram smi_driver; do
    [[ -n $bus && -z ${listed[$bus]-} ]] || continue
    driver=""
    if [[ -n $smi_driver ]]; then driver="nvidia $smi_driver"; fi
    printf '%s\n' "nvidia$FIELD_SEP$smi_name$FIELD_SEP$vram$FIELD_SEP$driver"
  done <<<"$smi_rows"
}

gpus_json() {
  local vendor model vram driver comma=""
  printf '['
  while IFS=$FIELD_SEP read -r vendor model vram driver; do
    [[ -n $vendor ]] || continue
    printf '%s\n    {\n      "vendor": "%s",\n      "model": %s,\n      "vramMb": %s,\n      "driver": %s\n    }' \
      "$comma" "$vendor" "$(json_str "$model")" "$(json_num "$vram")" "$(json_str "$driver")"
    comma=,
  done < <(list_gpus)
  if [[ -n $comma ]]; then printf '\n  '; fi
  printf ']\n'
}

# ---- RAM, disk, distro ----

ram_mb() { json_num "$(awk '/^MemTotal:/ { print int($2 / 1024) }' /proc/meminfo 2>/dev/null || true)"; }

disk_free_mb() {
  json_num "$(df -Pk "${HOME:-.}" 2>/dev/null | awk 'NR == 2 { print int($4 / 1024) }' || true)"
}

# os_release_field KEY: value of KEY in /etc/os-release, without quotes
os_release_field() { sed -n "s/^$1=//p" /etc/os-release 2>/dev/null | head -1 | tr -d "\"'" || true; }

distro_json() {
  printf '{\n    "id": %s,\n    "name": %s,\n    "version": %s\n  }\n' \
    "$(json_str "$(os_release_field ID)")" "$(json_str "$(os_release_field NAME)")" \
    "$(json_str "$(os_release_field VERSION_ID)")"
}

# ---- Output ----

main() {
  scan_vulkan # before the GPUs: they may need its output
  printf '{\n  "schemaVersion": 1,\n'
  printf '  "cpu": %s,\n' "$(cpu_json)"
  printf '  "gpus": %s,\n' "$(gpus_json)"
  printf '  "ramMb": %s,\n' "$(ram_mb)"
  printf '  "diskFreeMb": %s,\n' "$(disk_free_mb)"
  printf '  "distro": %s,\n' "$(distro_json)"
  printf '  "kernel": %s,\n' "$(json_str "$(uname -r 2>/dev/null || true)")"
  printf '  "vulkan": %s\n}\n' "$(vulkan_json)"
}

# run only when executed, not when sourced (tests source this file)
if [[ ${BASH_SOURCE[0]:-$0} == "$0" ]]; then main "$@"; fi
