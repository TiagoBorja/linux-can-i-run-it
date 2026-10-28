package io.github.tiagoborja.canirun.games;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of comparing one stored report with the requirements of one game.")
record GameComparison(
		@Schema(description = "The game.") GameSummary game,
		@Schema(description = "Result for the minimum requirements.") LevelResult minimum,
		@Schema(description = "Result for the recommended requirements.") LevelResult recommended) {
}
