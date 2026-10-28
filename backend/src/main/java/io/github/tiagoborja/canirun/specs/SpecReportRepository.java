package io.github.tiagoborja.canirun.specs;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SpecReportRepository extends JpaRepository<SpecReport, UUID> {

	Optional<SpecReport> findByCodeAndExpiresAtAfter(String code, Instant now);

	@Modifying
	@Query("delete from SpecReport r where r.expiresAt <= :now")
	int deleteExpired(Instant now);
}
