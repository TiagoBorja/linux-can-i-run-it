package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
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
                .containsExactlyInAnyOrder("201", "400", "413", "415", "500");
        assertThat(jsonRef(POST, "201")).endsWith("/Created");
        assertThat(docs.<String>read(POST + ".responses['201'].headers.Location.schema.type")).isEqualTo("string");
        for (String code : List.of("400", "413", "415", "500")) {
            assertThat(jsonRef(POST, code)).as(code).endsWith("/ApiError");
        }
    }

    @Test
    void shouldListGetResponses() {
        assertThat(docs.<Map<String, Object>>read(GET + ".responses").keySet())
                .containsExactlyInAnyOrder("200", "404", "500");
        assertThat(jsonRef(GET, "200")).endsWith("/SpecReportRequest");
        assertThat(jsonRef(GET, "404")).endsWith("/ApiError");
        assertThat(jsonRef(GET, "500")).endsWith("/ApiError");
    }

    @Test
    void shouldDescribeApiErrorSchema() {
        Map<String, Map<String, Object>> properties = docs.read("$.components.schemas.ApiError.properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrder("status", "error", "message", "timestamp");
        properties.forEach((field, property) -> {
            assertThat((String) property.get("description")).as(field).isNotBlank();
            assertThat(property.get("example")).as(field).isNotNull();
        });
    }

    @Test
    void shouldDescribeEveryRequestBodyField() {
        String ref = docs.read(POST + ".requestBody.content['application/json'].schema.$ref");
        List<String> checked = new ArrayList<>();
        assertFieldsDescribed(schemaName(ref), "", checked);
        assertThat(checked).containsExactlyInAnyOrder("schemaVersion", "cpu", "cpu.model", "cpu.cores",
                "cpu.threads", "gpus", "gpus.vendor", "gpus.model", "gpus.vramMb", "gpus.driver", "ramMb",
                "diskFreeMb", "distro", "distro.id", "distro.name", "distro.version", "kernel", "vulkan",
                "vulkan.supported", "vulkan.apiVersion");
    }

    @Test
    void shouldServeSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void shouldRedirectSwaggerUiHtmlToIndex() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    /** Checks each property of the schema and of the schemas it references. A free text field has maxLength 200. */
    @SuppressWarnings("unchecked")
    private void assertFieldsDescribed(String schema, String prefix, List<String> checked) {
        String base = "$.components.schemas." + schema + ".properties";
        Map<String, Map<String, Object>> properties = docs.read(base);
        properties.forEach((field, property) -> {
            String name = prefix + field;
            checked.add(name);
            assertThat(describe(base + "." + field)).as(name).isNotBlank();
            if ("string".equals(property.get("type")) && !property.containsKey("pattern")) {
                assertThat(property.get("maxLength")).as(name).isEqualTo(200);
            }
            Object items = property.get("items");
            Object nested = items instanceof Map ? ((Map<String, Object>) items).get("$ref") : property.get("$ref");
            if (nested != null) {
                assertFieldsDescribed(schemaName(nested.toString()), name + ".", checked);
            }
        });
    }

    /** The description sits on the property, or on the schema it references. */
    private String describe(String propertyPath) {
        String own = docs.read(propertyPath + ".description", String.class);
        if (own != null && !own.isBlank()) {
            return own;
        }
        String ref = docs.read(propertyPath + ".$ref", String.class);
        return ref == null ? null
                : docs.read("$.components.schemas." + schemaName(ref) + ".description", String.class);
    }

    private String jsonRef(String op, String code) {
        return docs.read(op + ".responses['" + code + "'].content['application/json'].schema.$ref");
    }

    private static String schemaName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }
}
