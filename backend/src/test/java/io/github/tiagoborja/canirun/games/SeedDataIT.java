package io.github.tiagoborja.canirun.games;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.tiagoborja.canirun.specs.AbstractIT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks the real seed data in the database that Flyway filled. */
class SeedDataIT extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbc;

    private static String detectSample() throws Exception {
        return Files.readString(Path.of("src/test/resources/specs/detect-sample.json"), StandardCharsets.UTF_8);
    }

    @Test
    void shouldHaveGamesWhenSeedIsLoaded() {
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isGreaterThanOrEqualTo(10);
    }

    @Test
    void shouldHaveMinimumAndRecommendedLevelForEveryGame() {
        List<String> incomplete = jdbc.queryForList(
                "select g.slug from game g where (select count(distinct r.level) from game_requirement r "
                        + "where r.game_slug = g.slug and r.level in ('minimum', 'recommended')) <> 2",
                String.class);

        assertThat(incomplete).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "vram_mb", "ram_mb", "disk_mb" })
    void shouldNotHaveMinimumAboveRecommendedWhenColumnIsNumeric(String column) {
        List<String> violations = jdbc.queryForList(
                "select m.game_slug from game_requirement m join game_requirement r "
                        + "on r.game_slug = m.game_slug and r.level = 'recommended' "
                        + "where m.level = 'minimum' and m." + column + " > r." + column,
                String.class);

        assertThat(violations).as(column).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "cpu_tier, cpu_key", "gpu_tier, gpu_key" })
    void shouldNotHaveMinimumTierAboveRecommendedTier(String tierTable, String keyColumn) {
        List<String> violations = jdbc.queryForList(
                "select m.game_slug from game_requirement m join game_requirement r "
                        + "on r.game_slug = m.game_slug and r.level = 'recommended' "
                        + "join " + tierTable + " mt on mt.key = m." + keyColumn + " "
                        + "join " + tierTable + " rt on rt.key = r." + keyColumn + " "
                        + "where m.level = 'minimum' and mt.tier > rt.tier",
                String.class);

        assertThat(violations).as(tierTable).isEmpty();
    }

    @Test
    void shouldResolveDetectSampleCpuToATier() throws Exception {
        String model = JsonPath.read(detectSample(), "$.cpu.model");
        List<String> keys = jdbc.queryForList("select key from cpu_tier", String.class);

        assertThat(TierMatcher.match(model, keys)).as(model).isPresent();
    }

    @Test
    void shouldResolveEveryDetectSampleGpuToATier() throws Exception {
        List<String> models = JsonPath.read(detectSample(), "$.gpus[*].model");
        List<String> keys = jdbc.queryForList("select key from gpu_tier", String.class);

        assertThat(models).hasSize(2);
        for (String model : models) {
            assertThat(TierMatcher.match(model, keys)).as(model).isPresent();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "cpu_tier", "gpu_tier" })
    void shouldStoreOnlyNormalizedNonBlankTierKeys(String table) {
        List<String> keys = jdbc.queryForList("select key from " + table, String.class);
        List<String> notNormalized = new ArrayList<>();
        for (String key : keys) {
            if (key.isBlank() || !key.equals(TierMatcher.normalize(key))) {
                notNormalized.add(key);
            }
        }

        assertThat(keys).as(table).isNotEmpty();
        assertThat(notNormalized).as(table).isEmpty();
    }
}
