package io.github.tiagoborja.canirun.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Schema v1 report from detect.sh. Nullable fields stay nullable (a missing tool gives null). */
public record SpecReportRequest(
		@Schema(description = "Report format version. Only 1 is accepted.", example = "1") @NotNull @Min(1) @Max(1) Integer schemaVersion,
		@Schema(description = "CPU details.") @NotNull @Valid Cpu cpu,
		@Schema(description = "One entry per GPU, or an empty list. 8 items maximum.") @NotNull @Size(max = 8) List<@NotNull @Valid Gpu> gpus,
		@Schema(description = "Total RAM in MB, from /proc/meminfo MemTotal. Null when /proc/meminfo has no MemTotal.", example = "15163") @PositiveOrZero @Max(16_777_216) Integer ramMb,
		@Schema(description = "Free disk space in MB for the home directory, from df. Null when df fails.", example = "106539") @PositiveOrZero @Max(1_000_000_000) Integer diskFreeMb,
		@Schema(description = "Linux distribution details.") @NotNull @Valid Distro distro,
		@Schema(description = "Kernel release, from uname -r. Null when uname fails.", example = "6.8.0-45-generic", maxLength = 200) @Text String kernel,
		@Schema(description = "Vulkan support, from vulkaninfo --summary.") @NotNull @Valid Vulkan vulkan) {

	public record Cpu(
			@Schema(description = "Model name, from lscpu. Null when lscpu is missing.", example = "AMD Ryzen 7 170 with Radeon Graphics", maxLength = 200) @Text String model,
			@Schema(description = "Physical cores in total, from lscpu (cores per socket x sockets). Null when lscpu is missing.", example = "8") @PositiveOrZero @Max(4096) Integer cores,
			@Schema(description = "Logical CPUs, from lscpu CPU(s). Null when lscpu is missing.", example = "16") @PositiveOrZero @Max(4096) Integer threads) {
	}

	public record Gpu(
			@Schema(description = "GPU vendor: nvidia, amd, intel or other.", example = "nvidia") @NotNull @Pattern(regexp = "nvidia|amd|intel|other") String vendor,
			@Schema(description = "Model name.", example = "NVIDIA GeForce RTX 5050 Laptop GPU", maxLength = 200) @Text String model,
			@Schema(description = "GPU VRAM in MB. Null when unknown.", example = "8151") @PositiveOrZero @Max(1_048_576) Integer vramMb,
			@Schema(description = "Driver name and version, from nvidia-smi (NVIDIA), or glxinfo or vulkaninfo (AMD, Intel). Null when unknown.", example = "mesa 24.0.5", maxLength = 200) @Text String driver) {
	}

	public record Distro(
			@Schema(description = "Distro ID from /etc/os-release. Null when /etc/os-release has no ID.", example = "pop", maxLength = 200) @Text String id,
			@Schema(description = "Distro name from /etc/os-release. Null when /etc/os-release has no NAME.", example = "Pop!_OS", maxLength = 200) @Text String name,
			@Schema(description = "Distro version from /etc/os-release VERSION_ID. Null when /etc/os-release has no VERSION_ID.", example = "24.04", maxLength = 200) @Text String version) {
	}

	public record Vulkan(
			@Schema(description = "True if vulkaninfo finds a GPU device. The CPU device (llvmpipe) does not count. False if vulkaninfo fails or finds only the CPU device. Null when vulkaninfo is missing or does not finish in 5 seconds.", example = "true") Boolean supported,
			@Schema(description = "Vulkan API version of the first GPU device. Null when supported is not true, or when the GPU device does not report it.", example = "1.3.275", maxLength = 200) @Text String apiVersion) {
	}
}
