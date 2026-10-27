# Manual test: detect.sh

Phase 1 has no HTTP API. So there are no `curl` cases yet.

The expected results below match the automated self-check in `detect/test_detect.sh`. The pr-reviewer agent runs the real check.

## Prerequisites

- Linux
- Bash 4 or later (`bash --version`)
- `jq` for pretty print and the self-check only. The script does not need it.
- Optional tools: `lscpu`, `lspci`, `nvidia-smi`, `glxinfo`, `vulkaninfo`. Missing tools give `null`. See [What the script collects](detect.md#missing-tools).

## How to run

Run these commands from the repository root:

```bash
bash detect/detect.sh
bash detect/detect.sh | jq .
bash detect/test_detect.sh
```

## Test data

None. Use your own machine.

## Test cases

### TC1: valid JSON

```bash
bash detect/detect.sh | jq . > /dev/null; echo "exit=$?"
```

- Expected: `exit=0`. The output has `"schemaVersion": 1`.
- Fail if: `jq` reports a parse error or the exit code is not `0`.

### TC2: values match your machine

```bash
lscpu | grep -E '^(Model name|CPU\(s\):|Core\(s\) per socket|Socket\(s\))'
bash detect/detect.sh | jq '{cpu, ramMb, diskFreeMb, kernel, distro}'
free -m | awk '/^Mem:/ {print "total MB:", $2}'
df -h ~ | tail -1
uname -r
grep -E '^(ID|NAME|VERSION_ID)=' /etc/os-release
nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader,nounits
```

- Expected:
  - `cpu.model` equals "Model name". `cpu.threads` equals "CPU(s)". `cpu.cores` equals "Core(s) per socket" x "Socket(s)".
  - `ramMb` equals "total MB" (within 1 MB).
  - `diskFreeMb` / 1024 matches the Avail column of `df -h ~` (`df` rounds, so a small difference is normal).
  - `kernel` equals `uname -r`.
  - `distro.id`, `distro.name` and `distro.version` equal ID, NAME and VERSION_ID (quotes removed).
  - The NVIDIA entry in `gpus` matches the `nvidia-smi` row (name, memory, driver). Skip this check if you have no NVIDIA GPU.
- Fail if: any value differs from the tool output.

### TC3: no private data

```bash
OUT=$(bash detect/detect.sh)
echo "$OUT" | grep -c -F "$(hostname)"
echo "$OUT" | grep -c -F "$USER"
ip link | awk '/link\/ether/ {print $2}' | while read -r MAC; do echo "$OUT" | grep -c -F "$MAC"; done
```

- Expected: every count is `0`.
- Fail if: any count is greater than `0`. A short hostname can match a normal word. For example, a hostname `pop` matches `"id": "pop"`. Check the matching line before you report a bug.

### TC4: missing tools

```bash
BIN=$(mktemp -d)
for T in awk grep sed df uname cat tr head cut; do ln -s "$(command -v "$T")" "$BIN/$T"; done
env PATH="$BIN" "$(command -v bash)" detect/detect.sh | jq .
rm -rf "$BIN"
```

- Expected: valid JSON and exit status `0`. `cpu` fields are `null`. `gpus` is `[]`. `vulkan` is `{"supported": null, "apiVersion": null}`. `ramMb`, `diskFreeMb`, `distro` and `kernel` have values.
- Fail if: the script exits with an error, prints invalid JSON, or a missing tool stops the script.

### TC5: hybrid laptop

Run this on a hybrid laptop (integrated GPU and NVIDIA GPU). If your machine has one GPU, skip TC5 and say so in your report.

```bash
lspci -mm -nn -D | grep -cE '"(VGA|3D|Display)'
bash detect/detect.sh | jq '.gpus | length, [.gpus[].vendor]'
```

- Expected: both counts are `2`. The vendors are `["nvidia","amd"]` (or `intel` for the integrated GPU). The NVIDIA entry has `vramMb` and `driver` from `nvidia-smi`.
- Fail if: the GPU count differs from the `lspci` count.

### TC6: no NVIDIA driver

This test hides `nvidia-smi` from the script. It leaves `lspci` available.

```bash
BIN=$(mktemp -d)
for T in awk grep sed df uname cat tr head cut lspci; do ln -s "$(command -v "$T")" "$BIN/$T"; done
env PATH="$BIN" "$(command -v bash)" detect/detect.sh | jq '.gpus'
rm -rf "$BIN"
```

- Expected: the NVIDIA entry has `"vendor": "nvidia"`, `"vramMb": null` and `"driver": null`. Its `model` comes from `lspci`, or is `null` if the name is unknown. Other GPU entries stay the same.
- Fail if: the script fails, or the NVIDIA entry has a `vramMb` or `driver` value.
