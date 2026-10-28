package io.github.tiagoborja.canirun.games;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of one part of a level.")
record Part(
		@Schema(description = "pass, fail or unknown. A part with no requirement is pass.") Result result,
		@Schema(description = "The required value: a tier name (CPU, GPU), MB (VRAM, RAM, disk) or a version (Vulkan). Null when the game states no requirement.", example = "8192") Object required) {
}
