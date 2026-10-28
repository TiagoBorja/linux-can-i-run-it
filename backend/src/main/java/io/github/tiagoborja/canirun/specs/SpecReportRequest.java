package io.github.tiagoborja.canirun.specs;

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
		@NotNull @Min(1) @Max(1) Integer schemaVersion,
		@NotNull @Valid Cpu cpu,
		@NotNull @Size(max = 8) List<@NotNull @Valid Gpu> gpus,
		@PositiveOrZero @Max(16_777_216) Integer ramMb,
		@PositiveOrZero @Max(1_000_000_000) Integer diskFreeMb,
		@NotNull @Valid Distro distro,
		@Text String kernel,
		@NotNull @Valid Vulkan vulkan) {

	public record Cpu(
			@Text String model,
			@PositiveOrZero @Max(4096) Integer cores,
			@PositiveOrZero @Max(4096) Integer threads) {
	}

	public record Gpu(
			@NotNull @Pattern(regexp = "nvidia|amd|intel|other") String vendor,
			@Text String model,
			@PositiveOrZero @Max(1_048_576) Integer vramMb,
			@Text String driver) {
	}

	public record Distro(
			@Text String id,
			@Text String name,
			@Text String version) {
	}

	public record Vulkan(
			Boolean supported,
			@Text String apiVersion) {
	}
}
