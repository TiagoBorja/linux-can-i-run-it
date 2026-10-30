package io.github.tiagoborja.canirun.games;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result for one level (minimum or recommended).")
record LevelResult(
		@Schema(description = "fail if any part fails, else unknown if any part is unknown, else pass.") Result result,
		@Schema(description = "Result of each part.") Parts parts) {
}
