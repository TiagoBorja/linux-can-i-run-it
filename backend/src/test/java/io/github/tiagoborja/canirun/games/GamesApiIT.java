package io.github.tiagoborja.canirun.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.tiagoborja.canirun.specs.AbstractIT;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The seed can change. These tests use their own rows (slug test-*, keys testcpu* and testgpu*). A tier of 1
 * always passes and a tier of 1000000 always fails, so no result depends on a seed tier value.
 */
@AutoConfigureMockMvc
@Sql(scripts = { "/games/test-games-cleanup.sql", "/games/test-games.sql" })
@Sql(scripts = "/games/test-games-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class GamesApiIT extends AbstractIT {

    private static final String COMPARE = "/api/specs/{code}/games/{slug}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("delete from spec_report");
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/specs/" + name), StandardCharsets.UTF_8);
    }

    /** Posts a report through the API and returns its code. */
    private String postReport(String json) throws Exception {
        String body = mvc.perform(post("/api/specs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private static String part(String result, String required) {
        return "{\"result\":\"" + result + "\",\"required\":" + required + "}";
    }

    /** Parts in order: cpu, gpu, vram, ram, disk, vulkan. */
    private static String level(String result, String cpu, String gpu, String vram, String ram, String disk,
            String vulkan) {
        return "{\"result\":\"" + result + "\",\"parts\":{\"cpu\":" + cpu + ",\"gpu\":" + gpu + ",\"vram\":" + vram
                + ",\"ram\":" + ram + ",\"disk\":" + disk + ",\"vulkan\":" + vulkan + "}}";
    }

    private static String body(String slug, String name, String minimum, String recommended) {
        return "{\"game\":{\"slug\":\"" + slug + "\",\"name\":\"" + name + "\"},\"minimum\":" + minimum
                + ",\"recommended\":" + recommended + "}";
    }

    // ---- GET /api/games -------------------------------------------------------------------------

    @Test
    void shouldListGamesSortedByNameWhenGamesExist() throws Exception {
        String json = mvc.perform(get("/api/games")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(json, "$[*].name");
        assertThat(names).isEqualTo(jdbc.queryForList("select name from game order by name", String.class));
        List<String> testSlugs = JsonPath.read(json, "$[?(@.slug =~ /test-.*/)].slug");
        assertThat(testSlugs).containsExactly("test-alpha", "test-compare", "test-nulls", "test-zulu");
    }

    @Test
    void shouldListOnlySlugAndNameForEachGame() throws Exception {
        String json = mvc.perform(get("/api/games")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> compare = JsonPath.read(json, "$[?(@.slug == 'test-compare')]");
        assertThat(compare).containsExactly(Map.of("slug", "test-compare", "name", "Test Compare Game"));
        List<Map<String, Object>> all = JsonPath.read(json, "$");
        assertThat(all).allSatisfy(game -> assertThat(game.keySet()).containsExactlyInAnyOrder("slug", "name"));
    }

    @Test
    void shouldReturnMethodNotAllowedWhenPostingToGames() throws Exception {
        assertFrameworkError(mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content("{}")),
                405, "METHOD_NOT_ALLOWED");
    }

    // ---- GET /api/specs/{code}/games/{slug} -----------------------------------------------------

    @Test
    void shouldCompareGameWhenReportIsDetectSample() throws Exception {
        String code = postReport(fixture("detect-sample.json"));

        String expected = body("test-compare", "Test Compare Game",
                level("unknown",
                        part("pass", "\"Test CPU Low\""), part("pass", "\"Test GPU Low\""),
                        part("pass", "4096"), part("pass", "8192"), part("pass", "10000"),
                        part("unknown", "\"1.1\"")),
                level("fail",
                        part("fail", "\"Test CPU High\""), part("fail", "\"Test GPU High\""),
                        part("pass", "8192"), part("pass", "16384"), part("fail", "200000"),
                        part("unknown", "\"1.3\"")));

        mvc.perform(get(COMPARE, code, "test-compare")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expected, true));
    }

    @Test
    void shouldReturnUnknownForEveryPartWhenReportIsAllNull() throws Exception {
        String code = postReport(fixture("all-null.json"));

        String expected = body("test-compare", "Test Compare Game",
                level("unknown",
                        part("unknown", "\"Test CPU Low\""), part("unknown", "\"Test GPU Low\""),
                        part("unknown", "4096"), part("unknown", "8192"), part("unknown", "10000"),
                        part("unknown", "\"1.1\"")),
                level("unknown",
                        part("unknown", "\"Test CPU High\""), part("unknown", "\"Test GPU High\""),
                        part("unknown", "8192"), part("unknown", "16384"), part("unknown", "200000"),
                        part("unknown", "\"1.3\"")));

        mvc.perform(get(COMPARE, code, "test-compare")).andExpect(status().isOk())
                .andExpect(content().json(expected, true));
    }

    @Test
    void shouldPassPartsWithNullRequirementWhenReportIsAllNull() throws Exception {
        String code = postReport(fixture("all-null.json"));

        String expected = body("test-nulls", "Test Nulls Game",
                level("pass",
                        part("pass", "null"), part("pass", "null"), part("pass", "null"),
                        part("pass", "null"), part("pass", "null"), part("pass", "null")),
                level("unknown",
                        part("pass", "null"), part("pass", "null"), part("pass", "null"),
                        part("unknown", "20000"), part("pass", "null"), part("pass", "null")));

        mvc.perform(get(COMPARE, code, "test-nulls")).andExpect(status().isOk())
                .andExpect(content().json(expected, true));
    }

    /** Columns: Vulkan apiVersion in the report, minimum (needs 1.1) result, recommended (needs 1.3) result. */
    @ParameterizedTest
    @CsvSource({ "1.3.275, pass, pass", "1.2, pass, fail", "1.0.5, fail, fail", "garbage, unknown, unknown" })
    void shouldCompareVulkanVersionWhenGpuSupportsVulkan(String apiVersion, String minimum, String recommended)
            throws Exception {
        String report = fixture("all-null.json")
                .replace("\"supported\": null", "\"supported\": true")
                .replace("\"apiVersion\": null", "\"apiVersion\": \"" + apiVersion + "\"");
        String code = postReport(report);

        mvc.perform(get(COMPARE, code, "test-compare")).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimum.parts.vulkan.result").value(minimum))
                .andExpect(jsonPath("$.recommended.parts.vulkan.result").value(recommended));
    }

    @Test
    void shouldFailVulkanWhenGpuDoesNotSupportVulkan() throws Exception {
        String code = postReport(fixture("all-null.json").replace("\"supported\": null", "\"supported\": false"));

        mvc.perform(get(COMPARE, code, "test-compare")).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimum.parts.vulkan.result").value("fail"))
                .andExpect(jsonPath("$.minimum.result").value("fail"))
                .andExpect(jsonPath("$.recommended.parts.vulkan.result").value("fail"));
    }

    @Test
    void shouldNotRepeatTheUserValuesInTheResponse() throws Exception {
        String code = postReport(fixture("detect-sample.json"));

        mvc.perform(get(COMPARE, code, "test-compare")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Ryzen"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("15163"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("106261"))));
    }

    // ---- 404 SPECS_NOT_FOUND --------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "ZZZZZZZZZZ", "abc", "k7q2m9xd4p", "0000000000", "AAAAAAAAAAA" })
    void shouldReturnSpecsNotFoundWhenCodeIsUnknownOrMalformed(String code) throws Exception {
        assertSpecsNotFound(code);
    }

    @Test
    void shouldReturnSpecsNotFoundWhenReportIsExpired() throws Exception {
        jdbc.update("insert into spec_report (id, code, payload, schema_version, created_at, expires_at) "
                + "values (gen_random_uuid(), ?, ?::jsonb, 1, now() - interval '31 days', now() - interval '1 day')",
                "EXPRD00001", fixture("all-null.json"));

        assertSpecsNotFound("EXPRD00001");
    }

    private void assertSpecsNotFound(String code) throws Exception {
        String phase2 = mvc.perform(get("/api/specs/ZZZZZZZZZZ")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        ResultActions result = mvc.perform(get(COMPARE, code, "test-compare"));
        result.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("SPECS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value((String) JsonPath.read(phase2, "$.message")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(content().string(Matchers.not(Matchers.containsString(code))));
        assertErrorKeys(result.andReturn().getResponse().getContentAsString());
    }

    // ---- 404 GAME_NOT_FOUND ---------------------------------------------------------------------

    /** Slugs are already URL-encoded. */
    @ParameterizedTest
    @ValueSource(strings = { "no-such-game", "..%2Fx", "Test-Compare", "TEST-COMPARE", "-test", "test-", "test--compare",
            "test_compare", "test%20compare" })
    void shouldReturnGameNotFoundWhenSlugIsUnknownOrMalformed(String slug) throws Exception {
        assertGameNotFound(slug);
    }

    @Test
    void shouldReturnGameNotFoundWhenSlugHas101Chars() throws Exception {
        assertGameNotFound("a".repeat(101));
    }

    @Test
    void shouldReturnGameNotFoundWhenSlugHas100CharsAndDoesNotExist() throws Exception {
        assertGameNotFound("a".repeat(100));
    }

    private void assertGameNotFound(String encodedSlug) throws Exception {
        String code = postReport(fixture("all-null.json"));
        String decoded = URLDecoder.decode(encodedSlug, StandardCharsets.UTF_8);

        ResultActions result = mvc.perform(get(URI.create("/api/specs/" + code + "/games/" + encodedSlug)));
        result.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("GAME_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(content().string(Matchers.not(Matchers.containsString(decoded))))
                .andExpect(content().string(Matchers.not(Matchers.containsString(code))));
        assertErrorKeys(result.andReturn().getResponse().getContentAsString());
    }

    private static void assertErrorKeys(String body) {
        Map<String, Object> error = JsonPath.read(body, "$");
        assertThat(error.keySet()).containsExactlyInAnyOrder("status", "error", "message", "timestamp");
    }

    // ---- 405 ------------------------------------------------------------------------------------

    @Test
    void shouldReturnMethodNotAllowedWhenPostingToCompareUrl() throws Exception {
        String code = postReport(fixture("all-null.json"));

        assertFrameworkError(mvc.perform(post(COMPARE, code, "test-compare")
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 405, "METHOD_NOT_ALLOWED");
    }

    private static void assertFrameworkError(ResultActions result, int status, String error) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value("Request failed."))
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
