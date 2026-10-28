package io.github.tiagoborja.canirun.specs;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SpecReportCleanup {

	private static final Logger log = LoggerFactory.getLogger(SpecReportCleanup.class);

	private final SpecReportRepository repository;
	private final Clock clock;

	SpecReportCleanup(SpecReportRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public int deleteExpired() {
		int deleted = repository.deleteExpired(Instant.now(clock));
		log.info("Deleted {} expired spec reports", deleted);
		return deleted;
	}
}
