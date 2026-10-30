package io.github.tiagoborja.canirun.games;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tiagoborja.canirun.specs.AbstractIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Checks the database rules on game and game_requirement. Each test rolls back. */
@Transactional
class GameSchemaIT extends AbstractIT {

    private static final String REQUIREMENT = "insert into game_requirement "
            + "(game_slug, level, vram_mb, ram_mb, disk_mb, vulkan_version) values ('test-schema', 'minimum', ?, ?, ?, ?)";

    @Autowired
    private JdbcTemplate jdbc;

    private void insertGame(String slug) {
        jdbc.update("insert into game (slug, name) values (?, 'Test Schema Game')", slug);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Upper", "a--b", "-a", "a-", "a_b", "../x", "a b", "" })
    void shouldRejectGameWhenSlugDoesNotMatchApiPattern(String slug) {
        assertThatThrownBy(() -> insertGame(slug)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_slug_check");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "0, 1, 1, null, game_requirement_vram_mb_check",
            "-1, 1, 1, null, game_requirement_vram_mb_check",
            "1, 0, 1, null, game_requirement_ram_mb_check",
            "1, -1, 1, null, game_requirement_ram_mb_check",
            "1, 1, 0, null, game_requirement_disk_mb_check",
            "1, 1, -1, null, game_requirement_disk_mb_check",
            "1, 1, 1, garbage, game_requirement_vulkan_version_check",
            "1, 1, 1, 1, game_requirement_vulkan_version_check",
            "1, 1, 1, 1.3., game_requirement_vulkan_version_check",
            "1, 1, 1, v1.3, game_requirement_vulkan_version_check",
            "1, 1, 1, 1.2.3.4, game_requirement_vulkan_version_check" })
    void shouldRejectRequirementWhenValueBreaksCheck(Integer vram, Integer ram, Integer disk, String vulkan,
            String constraint) {
        insertGame("test-schema");

        assertThatThrownBy(() -> jdbc.update(REQUIREMENT, vram, ram, disk, vulkan))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "1, 1, 1, 1.3", "8192, 16384, 71680, 1.3.275", "null, null, null, null" })
    void shouldAcceptRequirementWhenValuesAreValidOrNull(Integer vram, Integer ram, Integer disk, String vulkan) {
        insertGame("test-schema");

        assertThatCode(() -> jdbc.update(REQUIREMENT, vram, ram, disk, vulkan)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptGameWhenSlugMatchesApiPattern() {
        assertThatCode(() -> insertGame("test-schema-2")).doesNotThrowAnyException();
    }
}
