package io.github.tiagoborja.canirun.games;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

@Schema(description = "Outcome of a check: pass, fail or unknown (the report or the tier table lacks the data).")
enum Result {
	PASS, FAIL, UNKNOWN;

	@JsonValue
	String json() {
		return name().toLowerCase(Locale.ROOT);
	}
}
