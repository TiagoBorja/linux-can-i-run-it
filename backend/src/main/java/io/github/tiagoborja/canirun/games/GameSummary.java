package io.github.tiagoborja.canirun.games;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A game in the list.")
record GameSummary(
		@Schema(description = "Game id for the compare URL. Lower case letters, digits and hyphens.", example = "cyberpunk-2077") String slug,
		@Schema(description = "Game name.", example = "Cyberpunk 2077") String name) {
}
