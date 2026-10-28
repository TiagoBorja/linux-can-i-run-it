package io.github.tiagoborja.canirun.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DatabindException;

/** Fixed messages only. Never echo input, field values or exception messages. */
@RestControllerAdvice
class SpecsExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(SpecsExceptionHandler.class);

	@Schema(name = "ApiError")
	record ApiError(int status, String error, String message, Instant timestamp) {
	}

	private final Clock clock;

	SpecsExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> notReadable(HttpMessageNotReadableException ex) {
		for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof PayloadTooLargeException) {
				return tooLarge();
			}
		}
		if (ex.getCause() instanceof DatabindException || ex.getCause() instanceof InputCoercionException) {
			return validationFailed();
		}
		return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON.");
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HandlerMethodValidationException.class })
	ResponseEntity<ApiError> validation() {
		return validationFailed();
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	ResponseEntity<ApiError> tooLarge() {
		return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large.");
	}

	@ExceptionHandler(SpecsNotFoundException.class)
	ResponseEntity<ApiError> notFound() {
		return build(HttpStatus.NOT_FOUND, "SPECS_NOT_FOUND", "Specs report not found.");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> other(Exception ex) {
		// Framework errors (405, 415, unknown path, ...) keep their own status.
		if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
			HttpStatusCode code = er.getStatusCode();
			HttpStatus status = HttpStatus.resolve(code.value());
			String name = status == null ? "BAD_REQUEST" : status.name();
			return build(code, name, "Request failed.");
		}
		log.error("Unhandled error", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
	}

	private ResponseEntity<ApiError> validationFailed() {
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request body is not valid.");
	}

	private ResponseEntity<ApiError> build(HttpStatusCode status, String error, String message) {
		return ResponseEntity.status(status)
				.body(new ApiError(status.value(), error, message, Instant.now(clock)));
	}
}
