package io.github.tiagoborja.canirun.specs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Schema v1 report from detect.sh. Nullable fields stay nullable (a missing tool gives null). */
public record SpecReportRequest(
		@NotNull @Max(1) @PositiveOrZero Integer schemaVersion,
		@NotNull @Valid Cpu cpu,
		@NotNull @Size(max = 8) List<@NotNull @Valid Gpu> gpus,
		@PositiveOrZero @Max(16_777_216) Integer ramMb,
		@PositiveOrZero @Max(1_000_000_000) Integer diskFreeMb,
		@NotNull @Valid Distro distro,
		@Size(max = 200) String kernel,
		@NotNull @Valid Vulkan vulkan) {

	public record Cpu(
			@Size(max = 200) String model,
			@PositiveOrZero @Max(4096) Integer cores,
			@PositiveOrZero @Max(4096) Integer threads) {
	}

	public record Gpu(
			@NotNull @Pattern(regexp = "nvidia|amd|intel|other") String vendor,
			@Size(max = 200) String model,
			@PositiveOrZero @Max(1_048_576) Integer vramMb,
			@Size(max = 200) String driver) {
	}

	public record Distro(
			@Size(max = 200) String id,
			@Size(max = 200) String name,
			@Size(max = 200) String version) {
	}

	public record Vulkan(
			Boolean supported,
			@Size(max = 200) String apiVersion) {
	}
}
