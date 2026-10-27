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

exit "$FAILED"
