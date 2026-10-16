package com.xiaoxi.facegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** Exercise the Java HTTP multipart / JSON boundary independently of mocked business tests. */
class FaceClientContractTest {
    HttpServer server;
    FaceClient client;
    ObjectMapper json = new ObjectMapper();
    AtomicReference<String> request = new AtomicReference<>();
    AtomicReference<String> key = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new FaceClient("http://127.0.0.1:" + server.getAddress().getPort(), "contract-test-key", json);
    }
    @AfterEach void stop() { server.stop(0); }
    void endpoint(String path, int status, Object response) {
        server.createContext(path, exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-Service-Key"));
            byte[] body = json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
    }
    @Test void extractSendsMultipartAndMapsFeature() {
        endpoint("/extract", 200, new Models.Feature(Collections.nCopies(128, 0.1), "sface-2021dec", java.util.List.of(1,2,3,4)));
        var feature = client.extract(new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image-content".getBytes(StandardCharsets.UTF_8)));
        assertThat(feature.feature()).hasSize(128);
        assertThat(request.get()).contains("name=\"file\"", "image-content");
        assertThat(key.get()).isEqualTo("contract-test-key");
    }
    @Test void matchSendsGalleryAndMapsIdentity() throws Exception {
        endpoint("/match", 200, new Models.Match(7L, 0.9, 0.1, false));
        var feature = Collections.nCopies(128, 0.1);
        var result = client.match(feature, java.util.List.of(new Models.Candidate(7, feature)), 0.45, 0.05);
        assertThat(result.personId()).isEqualTo(7L);
        var input = json.readTree(request.get());
        assertThat(input.path("gallery").get(0).path("feature").size()).isEqualTo(128);
        assertThat(input.path("threshold").asDouble()).isEqualTo(0.45);
    }
    @Test void translatesPythonErrorsWithoutLeakingResponse() {
        endpoint("/extract", 422, java.util.Map.of("detail", "未检测到人脸"));
        assertThatThrownBy(() -> client.extract(new MockMultipartFile("file", new byte[] {1})))
            .isInstanceOf(ApiException.class).hasMessage("未检测到人脸");
    }
}
