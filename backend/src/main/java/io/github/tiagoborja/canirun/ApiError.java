package io.github.tiagoborja.canirun;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ApiError", description = "Error body. The same shape for every error.")
public record ApiError(
		@Schema(description = "HTTP status code.", example = "400") int status,
		@Schema(description = "Fixed error code: MALFORMED_JSON, VALIDATION_FAILED, PAYLOAD_TOO_LARGE, SPECS_NOT_FOUND, GAME_NOT_FOUND, INTERNAL_ERROR, or the HTTP status name (for example NOT_FOUND) for other 4xx errors.", example = "VALIDATION_FAILED") String error,
		@Schema(description = "Short fixed text. It never repeats the input.", example = "Request body is not valid.") String message,
		@Schema(description = "ISO-8601 instant of the error.", example = "2026-10-27T10:00:00Z") Instant timestamp) {
}
