# What the script collects

`detect/detect.sh` reads your hardware and prints JSON to stdout. It is read-only. It needs no root and no network.

Compare these values with game requirements. Use the distro and driver values for Linux tips.

`gpus` is an array. It has one entry per GPU, or `[]` if none is found.

| Field | Example | Source | Why we need it |
|---|---|---|---|
| `schemaVersion` | `1` | Set in the script | Lets us change the format later. |
| `cpu.model` | `AMD Ryzen 7 170 with Radeon Graphics` | `lscpu`, "Model name" | Compare the CPU with the game minimum. |
| `cpu.cores` | `8` | `lscpu`, cores per socket x sockets | Compare with the game CPU minimum. |
| `cpu.threads` | `16` | `lscpu`, "CPU(s)" | Compare with the game CPU minimum. |
| `gpus[].vendor` | `nvidia` | `lspci -mm -nn -D`, vendor ID | Pick the right driver tips. Values: nvidia, amd, intel, other. |
| `gpus[].model` | `NVIDIA GeForce RTX 5050 Laptop GPU` | `nvidia-smi` (NVIDIA), else `lspci` | Compare with the game GPU list. |
| `gpus[].vramMb` | `8151` | `nvidia-smi` (NVIDIA), or `/sys/bus/pci/devices/<id>/mem_info_vram_total` (AMD, Intel) | Compare VRAM with the game minimum. |
| `gpus[].driver` | `nvidia 595.104.02` or `mesa 24.0.5` | `nvidia-smi` (NVIDIA), or `glxinfo -B` / `vulkaninfo --summary` (AMD, Intel) | Driver version for Linux tips. |
| `ramMb` | `15163` | `/proc/meminfo`, MemTotal | Compare RAM with the game minimum. |
| `diskFreeMb` | `106539` | `df -Pk "$HOME"`, Available | Check free space for the game install. |
| `distro.id` | `pop` | `/etc/os-release`, ID | Distro-specific tips. |
| `distro.name` | `Pop!_OS` | `/etc/os-release`, NAME | Show the distro name. |
| `distro.version` | `24.04` | `/etc/os-release`, VERSION_ID | Version-specific tips. |
| `kernel` | `6.8.0-45-generic` | `uname -r` | Find kernel-specific driver issues. |
| `vulkan.supported` | `true` | `vulkaninfo --summary` | Many games need Vulkan. Values: `true`, `false`, `null` (see below). |
| `vulkan.apiVersion` | `1.3.275` | `vulkaninfo --summary`, apiVersion of the first GPU that is not a CPU device | Compare with the Vulkan version a game needs. `null` when `supported` is not `true`, or when the GPU device does not report it. |

`vulkan.supported` values:

- `true`: `vulkaninfo` runs and it finds a GPU device. The CPU device (lavapipe, `llvmpipe`) does not count.
- `false`: `vulkaninfo` fails, or it finds only the CPU device.
- `null`: `vulkaninfo` is not installed, or it does not finish in 5 seconds.

## What the script never reads

The script never reads or prints:

- Hostname
- Username
- MAC address
- Serial numbers
- IP address
- Machine ID (`/etc/machine-id`)

## Missing tools

If a tool is missing, the fields it sets are `null`. The script still runs and exits with status 0.

- `lscpu` sets the `cpu` fields. It comes with util-linux, which is installed by default.
- `lspci` lists the GPUs. Without it, only GPUs found by `nvidia-smi` are listed.
- `nvidia-smi` sets the NVIDIA model, VRAM and driver. It comes with the NVIDIA driver. Without it, an NVIDIA GPU is still listed from `lspci`, with `vramMb` and `driver` set to `null`.
- `glxinfo` sets the Mesa driver for AMD and Intel. `vulkaninfo` is used as a fallback for it.
- `vulkaninfo` sets the `vulkan` fields.

`nvidia-smi`, `glxinfo` and `vulkaninfo` each stop after 5 seconds. A tool that times out counts as missing.

## Null GPU fields

A GPU field is `null` when its value is unknown:

- `model`: `lspci` does not know the device name (it shows "Device"). For NVIDIA, the `nvidia-smi` name is used when it exists.
- `vramMb`: the kernel or tool does not report VRAM. This happens with Intel shared memory, with an NVIDIA GPU and no `nvidia-smi`, or when `nvidia-smi` prints `[N/A]`.
- `driver`: an NVIDIA GPU has no `nvidia-smi`. Or an AMD or Intel GPU has neither `glxinfo` nor `vulkaninfo` to report a Mesa version.

## Text values

Text fields are cleaned before they are printed:

- Control characters (for example tabs and new lines) become spaces.
- Non-ASCII characters are dropped. For example, `Radeon™` becomes `Radeon`.
- A value with nothing left is `null`.

## Install the optional tools

```bash
# Fedora
sudo dnf install pciutils mesa-demos vulkan-tools

# Ubuntu and Pop!_OS
sudo apt install pciutils mesa-utils vulkan-tools
```

## Data sending

The script does not send data. Sending comes in a later phase. It will ask you first.
