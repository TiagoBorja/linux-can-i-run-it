package io.github.tiagoborja.canirun.specs;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Finds a stored report by code. The code check lives here only. */
@Component
public class SpecsLookup {

	private static final Pattern CODE = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{10}$");

	private final SpecReportRepository repository;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	SpecsLookup(SpecReportRepository repository, ObjectMapper objectMapper, Clock clock) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/** The stored report. Throws SpecsNotFoundException when the code is malformed, unknown or expired. */
	public SpecReportRequest find(String code) {
		return objectMapper.readValue(payload(code), SpecReportRequest.class);
	}

	/** The stored JSON as is. Same errors as find. */
	String payload(String code) {
		if (!CODE.matcher(code).matches()) {
			throw new SpecsNotFoundException();
		}
		return repository.findByCodeAndExpiresAtAfter(code, Instant.now(clock))
				.orElseThrow(SpecsNotFoundException::new).getPayload();
	}
}
