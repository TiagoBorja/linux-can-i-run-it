package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SpecReportCleanupIT extends AbstractIT {

    @Autowired
    private SpecReportCleanup cleanup;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("delete from spec_report");
    }

    private void insert(String code, String interval) {
        jdbc.update("insert into spec_report (id, code, payload, schema_version, created_at, expires_at) "
                + "values (gen_random_uuid(), ?, '{}'::jsonb, 1, now(), now() + ?::interval)", code, interval);
    }

    @Test
    void shouldDeleteOnlyExpiredRowsWhenDeleteExpiredIsCalled() {
        insert("EXPRD00001", "-1 day");
        insert("EXPRD00002", "-1 second");
        insert("ACTV000001", "1 day");

        assertThat(cleanup.deleteExpired()).isEqualTo(2);

        List<String> codes = jdbc.queryForList("select code from spec_report", String.class);
        assertThat(codes).containsExactly("ACTV000001");
    }

    @Test
    void shouldReturnZeroWhenNoRowIsExpired() {
        insert("ACTV000001", "1 day");
        assertThat(cleanup.deleteExpired()).isZero();
    }

    @Test
    void shouldHaveOnlySixColumnsWhenSpecReportTableIsCreated() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'spec_report'", String.class);
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "code", "payload", "schema_version", "created_at", "expires_at");
    }
}
