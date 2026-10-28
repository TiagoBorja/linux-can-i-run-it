package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
    @MockitoSpyBean
    private CodeGenerator codeGenerator;

    @AfterEach
    void tearDown() {
        jdbc.update("delete from spec_report");
    }

    static String fixture(String name) throws Exception {
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
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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
    void shouldReturnValidationFailedWhenSchemaVersionIsZero() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"schemaVersion\": 1", "\"schemaVersion\": 0"),
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"ramMb\": null | \"ramMb\": \"123\"",
            "\"ramMb\": null | \"ramMb\": 1.5",
            "\"ramMb\": null | \"ramMb\": true",
            "\"ramMb\": null | \"ramMb\": \"\"",
            "\"kernel\": null | \"kernel\": 123",
            "\"kernel\": null | \"kernel\": 1.5",
            "\"kernel\": null | \"kernel\": true",
            "\"supported\": null | \"supported\": \"true\"",
            "\"supported\": null | \"supported\": 1" })
    void shouldReturnValidationFailedWhenScalarHasWrongType(String field, String wrongType) throws Exception {
        assertBadRequest(fixture("all-null.json").replace(field, wrongType), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWhenIntegerOverflows() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"ramMb\": null", "\"ramMb\": 99999999999"),
                "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnValidationFailedWithoutStoringWhenStringHasNul() throws Exception {
        assertBadRequest(fixture("all-null.json").replace("\"kernel\": null", "\"kernel\": \"a\\u0000b\""),
                "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from spec_report", Integer.class)).isZero();
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

    /** Each row: the field in detect-sample.json and its maximum. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"threads\": 16 | \"threads\": | 4096",
            "\"vramMb\": 8151 | \"vramMb\": | 1048576",
            "\"ramMb\": 15163 | \"ramMb\": | 16777216",
            "\"diskFreeMb\": 106261 | \"diskFreeMb\": | 1000000000" })
    void shouldAcceptMaxAndRejectMaxPlusOneWhenNumberIsAtLimit(String field, String key, long max) throws Exception {
        String sample = fixture("detect-sample.json");
        postSpecs(sample.replace(field, key + " " + max)).andExpect(status().isCreated());
        assertBadRequest(sample.replace(field, key + " " + (max + 1)), "VALIDATION_FAILED");
    }

    /** Each row: a string field in detect-sample.json. */
    @ParameterizedTest
    @ValueSource(strings = {
            "\"AMD Ryzen 7 170 with Radeon Graphics\"",
            "\"NVIDIA GeForce RTX 5050 Laptop GPU\"",
            "\"nvidia 595.104.02\"",
            "\"pop\"",
            "\"Pop!_OS\"",
            "\"24.04\"",
            "\"7.2.6-76070206-generic\"",
            "\"apiVersion\": null" })
    void shouldAccept200AndReject201CharsWhenStringIsAtLimit(String value) throws Exception {
        String sample = fixture("detect-sample.json");
        String prefix = value.startsWith("\"apiVersion\"") ? "\"apiVersion\": " : "";
        postSpecs(sample.replace(value, prefix + "\"" + "a".repeat(200) + "\"")).andExpect(status().isCreated());
        assertBadRequest(sample.replace(value, prefix + "\"" + "a".repeat(201) + "\""), "VALIDATION_FAILED");
    }

    @Test
    void shouldReturnCreatedWhenCodeCollidesOnce() throws Exception {
        insertActive("AAAAAAAAAA");
        doReturn("AAAAAAAAAA").doCallRealMethod().when(codeGenerator).next();
        postSpecs(fixture("all-null.json")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("AAAAAAAAAA")));
        verify(codeGenerator, times(2)).next();
        assertThat(jdbc.queryForObject("select count(*) from spec_report", Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldReturnInternalErrorWithoutDetailsWhenCodeCollidesThreeTimes() throws Exception {
        insertActive("AAAAAAAAAA");
        doReturn("AAAAAAAAAA").when(codeGenerator).next();
        postSpecs(fixture("all-null.json")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unique"))));
        verify(codeGenerator, times(3)).next();
        assertThat(jdbc.queryForObject("select count(*) from spec_report", Integer.class)).isEqualTo(1);
    }

    private void insertActive(String code) {
        jdbc.update("insert into spec_report (id, code, payload, schema_version, created_at, expires_at) "
                + "values (gen_random_uuid(), ?, '{}'::jsonb, 1, now(), now() + interval '1 day')", code);
    }

    @Test
    void shouldReturnUnsupportedMediaTypeWhenContentTypeIsNotJson() throws Exception {
        assertFrameworkError(mvc.perform(post("/api/specs").contentType(MediaType.TEXT_PLAIN).content("x")),
                415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void shouldReturnMethodNotAllowedWhenMethodIsPut() throws Exception {
        assertFrameworkError(mvc.perform(put("/api/specs").contentType(MediaType.APPLICATION_JSON).content("{}")),
                405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void shouldReturnNotFoundWhenPathIsUnknown() throws Exception {
        assertFrameworkError(mvc.perform(get("/api/unknown")), 404, "NOT_FOUND");
    }

    private static void assertFrameworkError(ResultActions result, int status, String error) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value("Request failed."))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void shouldAcceptBodyWhenItIsExactly16384Bytes() throws Exception {
        postSpecs(padded(BodySizeLimitFilter.MAX_BYTES)).andExpect(status().isCreated());
    }

    @Test
    void shouldReturnPayloadTooLargeWhenBodyIs16385Bytes() throws Exception {
        assertPayloadTooLarge(postSpecs(padded(BodySizeLimitFilter.MAX_BYTES + 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/specs;x=1", "/api/%73pecs" })
    void shouldReturnPayloadTooLargeWhenPathIsAVariantOfSpecs(String path) throws Exception {
        assertPayloadTooLarge(mvc.perform(post(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .content(padded(BodySizeLimitFilter.MAX_BYTES + 1))));
    }

    /** A valid report padded with leading spaces to exactly the given size in bytes. */
    static String padded(int bytes) throws Exception {
        String json = fixture("all-null.json");
        return " ".repeat(bytes - json.getBytes(StandardCharsets.UTF_8).length) + json;
    }

    static void assertPayloadTooLarge(ResultActions result) throws Exception {
        result.andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.timestamp").isString());
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
