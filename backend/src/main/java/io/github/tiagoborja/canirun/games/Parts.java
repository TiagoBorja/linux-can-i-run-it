package io.github.tiagoborja.canirun.games;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of each checked part.")
record Parts(
		@Schema(description = "CPU tier check.") Part cpu,
		@Schema(description = "GPU tier check. Passes if any GPU is fast enough.") Part gpu,
		@Schema(description = "VRAM check, in MB. Passes if any GPU has enough. 10% tolerance.") Part vram,
		@Schema(description = "RAM check, in MB. 10% tolerance.") Part ram,
		@Schema(description = "Free disk space check, in MB. No tolerance.") Part disk,
		@Schema(description = "Vulkan version check. Compares major.minor.") Part vulkan) {
}
