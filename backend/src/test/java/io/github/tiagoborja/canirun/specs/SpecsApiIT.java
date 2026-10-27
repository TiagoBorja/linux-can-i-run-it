package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class SpecsApiIT extends AbstractIT {

    private static final String MARKER = "SECRETMARKER";

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

    private ResultActions postSpecs(String body) throws Exception {
        return mvc.perform(post("/api/specs").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Builds a valid report with the given gpus array and cpu model. */
    private static String report(String cpuModel, String gpus) {
        return "{\"schemaVersion\":1,\"cpu\":{\"model\":" + cpuModel + ",\"cores\":8,\"threads\":16},"
                + "\"gpus\":" + gpus + ",\"ramMb\":1,\"diskFreeMb\":1,\"distro\":{\"id\":null,\"name\":null,"
                + "\"version\":null},\"kernel\":null,\"vulkan\":{\"supported\":null,\"apiVersion\":null}}";
    }

    private static String gpus(int n) {
        return "[" + String.join(",", java.util.Collections.nCopies(n,
                "{\"vendor\":\"other\",\"model\":null,\"vramMb\":null,\"driver\":null}")) + "]";
    }

    private void assertBadRequest(String body, String error) throws Exception {
        postSpecs(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(MARKER))));
    }

    private void assertRoundTrip(String json) throws Exception {
        MvcResult res = postSpecs(json).andExpect(status().isCreated()).andReturn();
        String body = res.getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");
        assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{10}");
        assertThat(res.getResponse().getHeader("Location")).isEqualTo("/api/specs/" + code);
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(Instant.now().plus(Duration.ofDays(30)), expiresAt).abs())
                .isLessThan(Duration.ofMinutes(1));
        mvc.perform(get("/api/specs/" + code)).andExpect(status().isOk())
                .andExpect(content().json(json, true));
    }

    @Test
    void shouldReturnSameSpecsWhenPostedDetectSampleIsRead() throws Exception {
        assertRoundTrip(fixture("detect-sample.json"));
    }

    @Test
    void shouldReturnSameSpecsWhenPostedAllNullReportIsRead() throws Exception {
        assertRoundTrip(fixture("all-null.json"));
    }

    @Test
    void shouldReturnMalformedJsonWhenBodyIsTruncated() throws Exception {
        assertBadRequest("{\"schemaVersion\":", "MALFORMED_JSON");
    }

    @Test
    void shouldReturnMalformedJsonWhenBodyIsNotJson() throws Exception {
        assertBadRequest("not json " + MARKER, "MALFORMED_JSON");
    }

    @Test
    void shouldReturnValidationFailedWhenSchemaVersionIsNotOne() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
                "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenSchemaVersionIsMissing() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"schemaVersion\": 1,", ""), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenRamMbHasWrongType() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"ramMb\": null", "\"ramMb\": \"lots" + MARKER + "\""),
                "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenTopLevelFieldIsUnknown() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"gpus\"", "\"" + MARKER + "\": 1, \"gpus\""),
                "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenNestedFieldIsUnknown() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"cores\": null", "\"" + MARKER + "\": 1"),
                "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenCpuIsNull() throws Exception {
        assertBadRequest(fixture("all-null.json").replaceAll("(?s)\"cpu\": \\{[^}]*\\}", "\"cpu\": null"),
                "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenGpusIsNull() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"gpus\": []", "\"gpus\": null"), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenGpuItemIsNull() throws Exception {
        assertBadRequest(report("null", "[null]"), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenGpuVendorIsMissing() throws Exception {
        assertBadRequest(report("null", "[{\"model\":null}]"), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenGpuVendorIsNotAllowed() throws Exception {
        assertBadRequest(report("null", "[{\"vendor\":\"" + MARKER + "\"}]"), "VALIDATION_FAILED");
    }

    @Test
    void shouldAcceptReportWhenStringIs200Chars() throws Exception {
        postSpecs(report("\"" + "a".repeat(200) + "\"", gpus(1))).andExpect(status().isCreated());
    }

    @Test
    void shouldReturnValidationFailedWhenStringIs201Chars() throws Exception {
        assertBadRequest(report("\"" + "a".repeat(201) + "\"", gpus(1)), "VALIDATION_FAILED");
    }

    @Test
    void shouldAcceptReportWhenThereAre8Gpus() throws Exception {
        postSpecs(report("null", gpus(8))).andExpect(status().isCreated());
    }

    @Test
    void shouldReturnValidationFailedWhenThereAre9Gpus() throws Exception {
        assertBadRequest(report("null", gpus(9)), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenNumberIsNegative() throws Exception {
        assertBadRequest(report("null", gpus(0)).replace("\"ramMb\":1", "\"ramMb\":-1"), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenCoresExceedMax() throws Exception {
        assertBadRequest(report("null", gpus(0)).replace("\"cores\":8", "\"cores\":4097"), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnPayloadTooLargeWhenBodyExceeds16384Bytes() throws Exception {
        String body = fixture("all-null.json") + " ".repeat(16385);
        postSpecs(body).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void shouldReturnNotFoundWhenCodeIsUnknown() throws Exception {
        assertNotFound("ZZZZZZZZZZ");
    }

    @Test
    void shouldReturnNotFoundWhenCodeHasBadFormat() throws Exception {
        assertNotFound("abc");
    }

    @Test
    void shouldReturnNotFoundWhenCodeIsLowerCase() throws Exception {
        assertNotFound("k7q2m9xd4p");
    }

    @Test
    void shouldReturnNotFoundWhenReportIsExpired() throws Exception {
        String payload = fixture("all-null.json");
        String sql = "insert into spec_report (id, code, payload, schema_version, created_at, expires_at) "
                + "values (gen_random_uuid(), ?, ?::jsonb, 1, now() - interval '31 days', now() + ?::interval)";
        jdbc.update(sql, "EXPRD00001", payload, "-1 day");
        jdbc.update(sql, "ACTV000001", payload, "1 day");
        assertNotFound("EXPRD00001");
        mvc.perform(get("/api/specs/ACTV000001")).andExpect(status().isOk())
                .andExpect(content().json(payload, true));
    }

    private void assertNotFound(String code) throws Exception {
        mvc.perform(get("/api/specs/" + code)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("SPECS_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(code))));
    }
}
