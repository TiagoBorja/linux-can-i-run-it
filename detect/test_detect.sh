#!/usr/bin/env bash
# Self-check for detect.sh. Run: bash detect/test_detect.sh
# shellcheck disable=SC2016,SC2329,SC1090  # jq filters use $vars; functions run via check; source path is dynamic
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DETECT="$DIR/detect.sh"

command -v jq >/dev/null || { echo "jq is required to run these tests" >&2; exit 2; }
[[ -f "$DETECT" ]] || { echo "detect.sh not found at $DETECT" >&2; exit 2; }

FAILED=0
check() { # check NAME CONDITION...  (condition is a command)
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then echo "PASS  $name"; else echo "FAIL  $name"; FAILED=1; fi
}

OUT=$(bash "$DETECT" 2>/dev/null)
j() { jq -e "$1" <<<"${2-$OUT}"; }  # j FILTER [JSON]

# ---- 1. Schema ----
check "valid JSON" j .
check "schemaVersion is 1" j '.schemaVersion == 1'
check "cpu types" j '.cpu | (.model|type) as $m | (.cores|type) as $c | (.threads|type) as $t
  | ($m=="string" or $m=="null") and ($c=="number" or $c=="null") and ($t=="number" or $t=="null")'
check "ramMb and diskFreeMb types" j '[.ramMb, .diskFreeMb] | all(type=="number" or type=="null")'
check "distro types" j '.distro | [.id, .name, .version] | all(type=="string" or type=="null")'
check "kernel type" j '.kernel | type=="string" or type=="null"'
check "vulkan types" j '.vulkan | (.supported|type) as $s | (.apiVersion|type) as $a
  | ($s=="boolean" or $s=="null") and ($a=="string" or $a=="null")'
check "gpus is array" j '.gpus | type=="array"'
check "gpu fields and vendor allowed" j '.gpus | all(
  (.vendor | IN("nvidia","amd","intel","other"))
  and ((.model|type)=="string" or (.model|type)=="null")
  and ((.vramMb|type)=="number" or (.vramMb|type)=="null")
  and ((.driver|type)=="string" or (.driver|type)=="null"))'

# ---- 2. Real values on this machine ----
if command -v lscpu >/dev/null; then
  threads=$(LC_ALL=C lscpu | awk -F: '/^CPU\(s\):/{gsub(/ /,"",$2); print $2}')
  check "cpu.threads == lscpu CPU(s)" j ".cpu.threads == $threads"
fi
check "kernel == uname -r" jq -e --arg k "$(uname -r)" '.kernel == $k' <<<"$OUT"
if [[ -r /etc/os-release ]]; then
  id=$(sed -n 's/^ID=//p' /etc/os-release | head -n1 | tr -d "\"'")
  check "distro.id == os-release ID" jq -e --arg v "$id" '.distro.id == $v' <<<"$OUT"
fi
mem=$(awk '/^MemTotal:/{print int($2/1024)}' /proc/meminfo)
check "ramMb == MemTotal/1024" j ".ramMb == $mem"
if command -v nvidia-smi >/dev/null && smi=$(nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader,nounits 2>/dev/null) && [[ -n "$smi" ]]; then
  IFS=',' read -r n v d <<<"$(head -n1 <<<"$smi")"
  n=${n# }; v=${v# }; d=${d# }
  check "nvidia gpu name, vram, driver match nvidia-smi" jq -e --arg n "$n" --arg d "nvidia $d" --argjson v "$v" \
    '[.gpus[] | select(.vendor=="nvidia" and .model==$n and .vramMb==$v and .driver==$d)] | length >= 1' <<<"$OUT"
fi
disk=$(df -Pk "$HOME" | awk 'NR==2{print int($4/1024)}')
check "diskFreeMb within 1% or 100 MB of df" j "(.diskFreeMb - $disk | fabs) <= ([100, $disk/100] | max)"

# ---- 3. Privacy ----
leak_free() { # leak_free VALUE: pass if VALUE is short/empty (too noisy to test) or absent from output
  [[ ${#1} -lt 3 ]] && return 0
  [[ "$OUT" != *"$1"* ]]
}
check "no hostname" leak_free "$(uname -n)"
check "no \$USER" leak_free "${USER:-}"
for f in /sys/class/net/*/address; do
  [[ -r $f ]] || continue
  mac=$(<"$f")
  [[ $mac == 00:00:00:00:00:00 ]] && continue
  check "no MAC from $f" leak_free "$mac"
done
[[ -r /etc/machine-id ]] && check "no machine-id" leak_free "$(</etc/machine-id)"

# ---- 4. Fallback: only required tools on PATH ----
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
for t in bash awk grep sed df uname cat tr head cut; do
  p=$(command -v "$t") && ln -s "$p" "$tmp/$t"
done
FB=$(PATH="$tmp" "$tmp/bash" "$DETECT" 2>/dev/null)
check "fallback: valid JSON" j . "$FB"
check "fallback: gpus == []" j '.gpus == []' "$FB"
check "fallback: vulkan.supported null" j '.vulkan.supported == null' "$FB"
check "fallback: cpu fields null" j '.cpu | .model == null and .cores == null and .threads == null' "$FB"
check "fallback: kernel and distro set" j '.kernel != null and .distro.id != null' "$FB"

# ---- 4b. Piped run (future curl | bash path) ----
PIPED=$(bash -s <"$DETECT" 2>/dev/null)
check "piped: valid JSON, schemaVersion 1" j '.schemaVersion == 1' "$PIPED"
PIPED_ARGS=$(cat "$DETECT" | bash -s ABC123 2>/dev/null)
check "piped with args: valid JSON, schemaVersion 1" j '.schemaVersion == 1' "$PIPED_ARGS"

# ---- 5. Escaping (source guard: detect.sh must not run main when sourced) ----
call() { ( source "$DETECT" >/dev/null 2>&1; "$@" ); }
str_roundtrip() { # str_roundtrip INPUT: json_str output must decode back to INPUT
  jq -e --arg in "$1" --argjson o "{\"v\": $(call json_str "$1")}" '$o.v == $in' <<<null
}
check "json_str quote and backslash" str_roundtrip 'a"b\c'
check "json_str newline, tab, CR, control char" str_roundtrip $'a\nb\tc\r\x01d'
check "json_str empty -> null" jq -e --argjson o "{\"v\": $(call json_str "")}" '$o.v == null' <<<null
check "json_num abc -> null" test "$(call json_num abc)" = null
check "json_num empty -> null" test "$(call json_num "")" = null
check "json_num 42 -> 42" test "$(call json_num 42)" = 42

# ---- 6. Parsing with stub tools (no GPU, nvidia-smi, glxinfo or vulkaninfo needed) ----
base=$(mktemp -d); trap 'rm -rf "$tmp" "$base"' EXIT
mkdir -p "$base/bin"
for t in bash awk grep sed df uname cat tr head cut timeout; do
  p=$(command -v "$t") && ln -s "$p" "$base/bin/$t"
done
stub() { # stub CASE TOOL: write stdin as the script of TOOL for CASE
  mkdir -p "$base/$1"; { echo '#!/bin/sh'; cat; } >"$base/$1/$2"; chmod +x "$base/$1/$2"
}
fake() { # fake CASE: run main with only the stubs of CASE and the base tools on PATH
  ( PATH="$base/$1:$base/bin"; source "$DETECT"; PCI_SYS="$base/$1/sys"; main ) 2>/dev/null
}
LSPCI_NV0='0000:01:00.0 "VGA compatible controller [0300]" "NVIDIA Corporation [10de]" "AD107M [GeForce RTX 4060 Max-Q / Mobile] [28e0]" -ra1 -p00 "Lenovo [17aa]" "Device [3c3d]"'
LSPCI_AMD='0000:65:00.0 "Display controller [0380]" "Advanced Micro Devices, Inc. [AMD/ATI] [1002]" "Phoenix1 [15bf]" -rc4 -p00 "Lenovo [17aa]" "Device [3c3d]"'

# Case good: NVIDIA in two PCI domains with the same bus, AMD with sysfs VRAM, Ubuntu Mesa,
# Vulkan with the CPU device first and the old packed apiVersion format.
stub good lscpu <<'EOF'
cat <<'X'
Architecture:                         x86_64
CPU(s) scaling MHz:                   50%
CPU(s):                               16
Model name:                           AMD Ryzen 7 7840HS w/ Radeon 780M Graphics
Core(s) per socket:                   8
Socket(s):                            1
X
EOF
stub good lspci <<EOF
cat <<'X'
$LSPCI_NV0
${LSPCI_NV0/#0000/0001}
0000:65:00.1 "Audio device [0403]" "Advanced Micro Devices, Inc. [AMD/ATI] [1002]" "Rembrandt Radeon High Definition Audio Controller [1640]" -p00 "Lenovo [17aa]" "Device [3c3d]"
$LSPCI_AMD
X
EOF
stub good nvidia-smi <<'EOF'
echo '00000000:01:00.0, NVIDIA GeForce RTX 4060 Laptop GPU, 8188, 550.54.14'
EOF
stub good glxinfo <<'EOF'
echo 'OpenGL version string: 4.6 (Compatibility Profile) Mesa 24.0.5-1ubuntu1'
EOF
stub good vulkaninfo <<'EOF'
cat <<'X'
Vulkan Instance Version: 1.3.275

Devices:
========
GPU0:
	apiVersion         = 1.3.255
	deviceType         = PHYSICAL_DEVICE_TYPE_CPU
	deviceName         = llvmpipe (LLVM 17.0.6, 256 bits)
GPU1:
	apiVersion         = 4206802 (1.3.274)
	deviceType         = PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
	deviceName         = AMD Radeon 780M (RADV GFX1103_R1)
X
EOF
mkdir -p "$base/good/sys/0000:65:00.0"; echo 536870912 >"$base/good/sys/0000:65:00.0/mem_info_vram_total"
G=$(fake good)
check "stub good: valid JSON" j . "$G"
check "stub good: cpu model, cores = cores x sockets, threads" j '.cpu == {"model": "AMD Ryzen 7 7840HS w/ Radeon 780M Graphics", "cores": 8, "threads": 16}' "$G"
check "stub good: audio device skipped, 3 GPUs" j '.gpus | length == 3' "$G"
check "stub good: nvidia-smi merged into domain 0000 only" j '.gpus[0] == {"vendor": "nvidia", "model": "NVIDIA GeForce RTX 4060 Laptop GPU", "vramMb": 8188, "driver": "nvidia 550.54.14"}' "$G"
check "stub good: domain 0001 keeps lspci model" j '.gpus[1] == {"vendor": "nvidia", "model": "GeForce RTX 4060 Max-Q / Mobile", "vramMb": null, "driver": null}' "$G"
check "stub good: amd vram from sysfs, dotted Mesa from glxinfo" j '.gpus[2] == {"vendor": "amd", "model": "Phoenix1", "vramMb": 512, "driver": "mesa 24.0.5"}' "$G"
check "stub good: vulkan from the non-CPU device, dotted apiVersion" j '.vulkan == {"supported": true, "apiVersion": "1.3.274"}' "$G"

# Case broken: nvidia-smi prints a driver error, vulkaninfo times out (exit 124).
stub broken lspci <<EOF
printf '%s\n' '$LSPCI_NV0' '$LSPCI_AMD'
EOF
stub broken nvidia-smi <<'EOF'
echo "NVIDIA-SMI has failed because it couldn't communicate with the NVIDIA driver. Make sure that the latest NVIDIA driver is installed and running."
exit 9
EOF
stub broken vulkaninfo <<'EOF'
exit 124
EOF
B=$(fake broken)
check "stub broken: valid JSON" j . "$B"
check "stub broken: no fake GPU from nvidia-smi error text" j '.gpus | length == 2' "$B"
check "stub broken: nvidia keeps lspci model, null driver" j '.gpus[0] == {"vendor": "nvidia", "model": "GeForce RTX 4060 Max-Q / Mobile", "vramMb": null, "driver": null}' "$B"
check "stub broken: no lscpu -> cpu fields null" j '.cpu | .model == null and .cores == null and .threads == null' "$B"
check "stub broken: vulkaninfo timeout -> supported null" j '.vulkan == {"supported": null, "apiVersion": null}' "$B"

# Case na: nvidia-smi prints empty and [N/A] values, Vulkan has only the CPU device.
stub na lspci <<EOF
printf '%s\n' '$LSPCI_NV0' '$LSPCI_AMD'
EOF
stub na nvidia-smi <<'EOF'
echo '00000000:01:00.0, , [N/A], [N/A]'
EOF
stub na vulkaninfo <<'EOF'
cat <<'X'
Devices:
========
GPU0:
	apiVersion         = 1.3.289
	deviceType         = PHYSICAL_DEVICE_TYPE_CPU
	deviceName         = llvmpipe (LLVM 19.1.1, 256 bits)
	driverInfo         = Mesa 24.2.8-1ubuntu1~24.04.1
X
EOF
N=$(fake na)
check "stub na: valid JSON" j . "$N"
check "stub na: empty and [N/A] keep lspci model, null vram and driver" j '.gpus[0] == {"vendor": "nvidia", "model": "GeForce RTX 4060 Max-Q / Mobile", "vramMb": null, "driver": null}' "$N"
check "stub na: no glxinfo -> dotted Mesa from vulkaninfo driverInfo" j '.gpus[1].driver == "mesa 24.2.8"' "$N"
check "stub na: only CPU device -> supported false, apiVersion null" j '.vulkan == {"supported": false, "apiVersion": null}' "$N"

exit "$FAILED"
