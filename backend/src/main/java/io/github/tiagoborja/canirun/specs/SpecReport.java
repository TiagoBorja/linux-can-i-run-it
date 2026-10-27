package io.github.tiagoborja.canirun.specs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "spec_report")
class SpecReport {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, unique = true, length = 10)
	private String code;

	/** The validated report, serialized back to JSON. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(name = "schema_version", nullable = false)
	private int schemaVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected SpecReport() {
	}

	SpecReport(String code, String payload, int schemaVersion, Instant createdAt, Instant expiresAt) {
		this.code = code;
		this.payload = payload;
		this.schemaVersion = schemaVersion;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	String getPayload() {
		return payload;
	}
}
