package com.harvestflow.api.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import com.harvestflow.api.service.image.AuthenticityResult;
import com.harvestflow.api.service.image.ExternalImageAuthenticityService;
import com.harvestflow.api.service.image.ImageAuthenticityService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Talks to a tiny fake AI service so the HTTP contract with ai-service/ is tested without Python. */
class AiServiceClientTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final AtomicReference<String> lastKey = new AtomicReference<>();

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route/optimize", ex -> reply(ex, 200,
                "{\"totalKm\":12.5,\"baselineKm\":20.0,\"unassigned\":[],\"routes\":[{\"stops\":[{\"id\":\"p2\"},{\"id\":\"p1\"}]}]}"));
        server.createContext("/image/verify", ex -> reply(ex, 200,
                "{\"decision\":\"MANUAL_REVIEW\",\"status\":\"SUSPICIOUS\",\"authenticityScore\":0.4,"
                        + "\"reasons\":[\"The same photo was already used on another seller's listing.\"],"
                        + "\"cropMatch\":{\"crop\":\"Tomato\",\"probability\":0.91},\"verifier\":\"harvestflow-ai-1.0.0\"}"));
        server.createContext("/market/mandi-price", ex -> reply(ex, 404, "{\"detail\":\"No mandi price\"}"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws java.io.IOException {
        lastKey.set(ex.getRequestHeaders().getFirst("X-AI-Key"));
        ex.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private AiServiceClient client(boolean enabled) {
        return new AiServiceClient(enabled, baseUrl, "secret-key", new ObjectMapper());
    }

    @Test
    void disabledClientNeverCalls() {
        assertThat(client(false).postJson("/route/optimize", Map.of(), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void postsJsonWithApiKeyAndParsesReply() {
        var res = client(true).postJson("/route/optimize", Map.of("stops", List.of()), Duration.ofSeconds(5));
        assertThat(res).isPresent();
        assertThat(res.get().path("totalKm").asDouble()).isEqualTo(12.5);
        assertThat(lastKey.get()).isEqualTo("secret-key");
    }

    @Test
    void errorStatusBecomesEmptyNotAnException() {
        assertThat(client(true).get("/market/mandi-price", Map.of("crop", "Dragon fruit"), Duration.ofSeconds(5))).isEmpty();
    }

    @Test
    void unreachableServiceBecomesEmpty() {
        var down = new AiServiceClient(true, "http://127.0.0.1:1", "k", new ObjectMapper());
        assertThat(down.postJson("/route/optimize", Map.of(), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void externalImageCheckMapsTheAiVerdict() throws Exception {
        Path img = tempPng();
        var checker = new ExternalImageAuthenticityService(client(true));
        AuthenticityResult r = checker.analyze(img, "image/png",
                new ImageAuthenticityService.ImageContext("farmer1", "L1", null, "Tomato", null, null, false));
        assertThat(r.status()).isEqualTo(ImageVerificationStatus.SUSPICIOUS);
        assertThat(r.status().decision().name()).isEqualTo("MANUAL_REVIEW");
        assertThat(r.reasons()).anyMatch(x -> x.contains("another seller"));
        assertThat(r.reasons()).anyMatch(x -> x.contains("Crop match (Tomato): 91%"));
    }

    @Test
    void externalImageCheckFallsBackWhenAiIsDown() throws Exception {
        Path img = tempPng();
        var down = new AiServiceClient(true, "http://127.0.0.1:1", "k", new ObjectMapper());
        AuthenticityResult r = new ExternalImageAuthenticityService(down).analyze(img, "image/png");
        assertThat(r.verifierId()).isEqualTo("heuristic-v1");
        assertThat(r.reasons()).anyMatch(x -> x.contains("AI service unavailable"));
    }

    private static Path tempPng() throws Exception {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 300; x += 3) image.setRGB(x, x, 0xC83C1E);
        Path p = Files.createTempFile("crop", ".png");
        ImageIO.write(image, "png", p.toFile());
        p.toFile().deleteOnExit();
        return p;
    }
}
