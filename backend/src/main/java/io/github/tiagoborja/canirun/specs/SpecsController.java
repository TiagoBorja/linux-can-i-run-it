package io.github.tiagoborja.canirun.specs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Tag(name = "Specs")
@RestController
@RequestMapping("/api/specs")
class SpecsController {

	private static final Pattern CODE = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{10}$");
	private static final Duration TTL = Duration.ofDays(30);
	private static final int MAX_TRIES = 3;
	private static final String CODE_CONSTRAINT = "spec_report_code_key";

	@Schema(name = "Created")
	record Created(
			@Schema(description = "Anonymous report code. 10 characters, Crockford base32.", example = "K7Q2M9XD4P") String code,
			@Schema(description = "ISO-8601 instant. The report expires 30 days after creation.", example = "2026-11-26T10:00:00Z") Instant expiresAt) {
	}

	private final SpecReportRepository repository;
	private final CodeGenerator codeGenerator;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	SpecsController(SpecReportRepository repository, CodeGenerator codeGenerator, ObjectMapper objectMapper,
			Clock clock) {
		this.repository = repository;
		this.codeGenerator = codeGenerator;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Operation(summary = "Store a schema v1 hardware report")
	@ApiResponse(responseCode = "201", description = "Report stored")
	@ApiResponse(responseCode = "400", description = "Malformed JSON or invalid body", content = @Content(schema = @Schema(implementation = SpecsExceptionHandler.ApiError.class)))
	@ApiResponse(responseCode = "413", description = "Body is larger than 16 KB", content = @Content(schema = @Schema(implementation = SpecsExceptionHandler.ApiError.class)))
	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Created> create(@Valid @RequestBody SpecReportRequest request) {
		String payload = objectMapper.writeValueAsString(request);
		Instant now = Instant.now(clock);
		Instant expiresAt = now.plus(TTL);
		// Not transactional on purpose: each save is its own transaction, so a collision can be retried.
		for (int i = 0; i < MAX_TRIES; i++) {
			String code = codeGenerator.next();
			try {
				repository.saveAndFlush(new SpecReport(code, payload, request.schemaVersion(), now, expiresAt));
				return ResponseEntity.created(URI.create("/api/specs/" + code)).body(new Created(code, expiresAt));
			} catch (DataIntegrityViolationException e) {
				if (!isCodeCollision(e)) {
					throw e;
				}
			}
		}
		throw new IllegalStateException("Could not create a unique code");
	}

	/** True only for a duplicate code. Other integrity errors are not retried. */
	private static boolean isCodeCollision(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException cve
				&& cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE
				&& CODE_CONSTRAINT.equals(cve.getConstraintName());
	}

	@Operation(summary = "Read a stored report by code")
	@ApiResponse(responseCode = "200", description = "The stored report", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SpecReportRequest.class)))
	@ApiResponse(responseCode = "404", description = "Code is unknown or expired", content = @Content(schema = @Schema(implementation = SpecsExceptionHandler.ApiError.class)))
	@GetMapping("/{code}")
	ResponseEntity<String> get(@PathVariable String code) {
		if (!CODE.matcher(code).matches()) {
			throw new SpecsNotFoundException();
		}
		SpecReport report = repository.findByCodeAndExpiresAtAfter(code, Instant.now(clock))
				.orElseThrow(SpecsNotFoundException::new);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(report.getPayload());
	}
}
