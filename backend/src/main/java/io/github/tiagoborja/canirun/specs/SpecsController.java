package io.github.tiagoborja.canirun.specs;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
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

@RestController
@RequestMapping("/api/specs")
class SpecsController {

	private static final Pattern CODE = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{10}$");
	private static final Duration TTL = Duration.ofDays(30);
	private static final int MAX_TRIES = 3;

	record Created(String code, Instant expiresAt) {
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

	@PostMapping
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
				// unique code collision: try a new code
			}
		}
		throw new IllegalStateException("Could not create a unique code");
	}

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
