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
		@Schema(description = "Total RAM in MB, from /proc/meminfo MemTotal.", example = "15163") @PositiveOrZero @Max(16_777_216) Integer ramMb,
		@Schema(description = "Free disk space in MB for the home directory.", example = "106539") @PositiveOrZero @Max(1_000_000_000) Integer diskFreeMb,
		@Schema(description = "Linux distribution details.") @NotNull @Valid Distro distro,
		@Schema(description = "Kernel release, from uname -r.", example = "6.8.0-45-generic") @Text String kernel,
		@NotNull @Valid Vulkan vulkan) {

	public record Cpu(
			@Schema(description = "Model name.", example = "AMD Ryzen 7 170 with Radeon Graphics") @Text String model,
			@Schema(description = "Physical cores.", example = "8") @PositiveOrZero @Max(4096) Integer cores,
			@PositiveOrZero @Max(4096) Integer threads) {
	}

	public record Gpu(
			@Schema(description = "GPU vendor: nvidia, amd, intel or other.", example = "nvidia") @NotNull @Pattern(regexp = "nvidia|amd|intel|other") String vendor,
			@Schema(description = "Model name.", example = "AMD Ryzen 7 170 with Radeon Graphics") @Text String model,
			@Schema(description = "GPU VRAM in MB. Null when unknown.", example = "8151") @PositiveOrZero @Max(1_048_576) Integer vramMb,
			@Text String driver) {
	}

	public record Distro(
			@Schema(description = "Distro ID from /etc/os-release.", example = "pop") @Text String id,
			@Schema(description = "Distro name from /etc/os-release.", example = "Pop!_OS") @Text String name,
			@Text String version) {
	}

	public record Vulkan(
			@Schema(description = "True if a Vulkan GPU device works. Null when vulkaninfo is missing or times out.", example = "true") Boolean supported,
			@Text String apiVersion) {
	}
}
