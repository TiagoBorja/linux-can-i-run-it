package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Sends real HTTP requests to Tomcat. A body with no Content-Length uses the counting path of the filter. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpecsChunkedBodyIT extends AbstractIT {

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("delete from spec_report");
    }

    /** Sends the body with no Content-Length, so the client uses chunked transfer encoding. */
    private HttpResponse<String> postChunked(String path, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertPayloadTooLarge(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat((String) JsonPath.read(response.body(), "$.error")).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat((Integer) JsonPath.read(response.body(), "$.status")).isEqualTo(413);
    }

    @Test
    void shouldAcceptChunkedBodyWhenItIsExactly16384Bytes() throws Exception {
        HttpResponse<String> response = postChunked("/api/specs", SpecsApiIT.padded(BodySizeLimitFilter.MAX_BYTES));
        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void shouldReturnPayloadTooLargeWhenChunkedBodyIs16385Bytes() throws Exception {
        assertPayloadTooLarge(postChunked("/api/specs", SpecsApiIT.padded(BodySizeLimitFilter.MAX_BYTES + 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/specs;x=1", "/api/%73pecs" })
    void shouldReturnPayloadTooLargeWhenChunkedBodyIsSentToAPathVariant(String path) throws Exception {
        assertPayloadTooLarge(postChunked(path, SpecsApiIT.padded(BodySizeLimitFilter.MAX_BYTES + 1)));
    }
}
