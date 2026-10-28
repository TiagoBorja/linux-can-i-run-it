package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class OpenApiDocsIT extends AbstractIT {

    private static final String POST = "$.paths['/api/specs'].post";
    private static final String GET = "$.paths['/api/specs/{code}'].get";

    @Autowired
    private MockMvc mvc;
    private DocumentContext docs;

    @BeforeEach
    void loadDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        docs = JsonPath.parse(body);
    }

    @Test
    void shouldDescribeApiTitleAndVersion() {
        assertThat(docs.<String>read("$.info.title")).isEqualTo("Can I Run It - Linux API");
        assertThat(docs.<String>read("$.info.version")).isEqualTo("1");
    }

    @ParameterizedTest
    @ValueSource(strings = { POST, GET })
    void shouldTagAndSummarizeOperation(String op) {
        assertThat(docs.<List<String>>read(op + ".tags")).containsExactly("Specs");
        assertThat(docs.<String>read(op + ".summary")).isNotBlank();
    }

    @Test
    void shouldListPostResponses() {
        assertThat(docs.<Map<String, Object>>read(POST + ".responses").keySet())
                .contains("201", "400", "413");
        assertErrorRef(POST, "400");
        assertErrorRef(POST, "413");
    }

    @Test
    void shouldListGetResponses() {
        assertThat(docs.<Map<String, Object>>read(GET + ".responses").keySet()).contains("200", "404");
        assertErrorRef(GET, "404");
    }

    @Test
    void shouldDescribeApiErrorSchema() {
        assertThat(docs.<Map<String, Object>>read("$.components.schemas.ApiError.properties").keySet())
                .contains("status", "error", "message", "timestamp");
    }

    @Test
    void shouldDescribeRequestBodyFields() {
        String ref = docs.read(POST + ".requestBody.content['application/json'].schema.$ref");
        String schema = "$.components.schemas." + ref.substring(ref.lastIndexOf('/') + 1) + ".properties.";
        for (String field : List.of("schemaVersion", "cpu", "gpus", "ramMb")) {
            String description = describe(schema + field);
            assertThat(description).as(field).isNotBlank();
        }
    }

    /** The description sits on the property, or on the schema it references. */
    private String describe(String propertyPath) {
        String own = docs.read(propertyPath + ".description", String.class);
        if (own != null && !own.isBlank()) {
            return own;
        }
        String ref = docs.read(propertyPath + ".$ref", String.class);
        return ref == null ? null
                : docs.read("$.components.schemas." + ref.substring(ref.lastIndexOf('/') + 1) + ".description",
                        String.class);
    }

    private void assertErrorRef(String op, String code) {
        String ref = docs.read(op + ".responses['" + code + "'].content.*.schema.$ref", List.class)
                .get(0).toString();
        assertThat(ref).endsWith("/ApiError");
    }

    @Test
    void shouldServeSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
