package com.harvestflow.api.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to the Python AI service (ai-service/). Every method returns
 * {@link Optional#empty()} instead of throwing when the service is turned off,
 * down, slow or returns an error, so callers can always fall back to the
 * built-in rule-based code and the app keeps working.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public AiServiceClient(@Value("${harvestflow.ai.enabled:false}") boolean enabled,
                           @Value("${harvestflow.ai.base-url:http://127.0.0.1:8000}") String baseUrl,
                           @Value("${harvestflow.ai.api-key:}") String apiKey,
                           ObjectMapper mapper) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.mapper = mapper;
        if (enabled) {
            log.info("AI service integration enabled at {}", this.baseUrl);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private RestClient client(Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("X-AI-Key", apiKey == null ? "" : apiKey)
                .build();
    }

    public Optional<JsonNode> postJson(String path, Object body, Duration timeout) {
        if (!enabled) return Optional.empty();
        try {
            String json = client(timeout).post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(json).map(this::parse);
        } catch (Exception e) {
            log.warn("AI service POST {} failed, using fallback: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonNode> get(String path, Map<String, String> params, Duration timeout) {
        if (!enabled) return Optional.empty();
        try {
            String json = client(timeout).get()
                    .uri(builder -> {
                        builder.path(path);
                        params.forEach((k, v) -> {
                            if (v != null && !v.isBlank()) builder.queryParam(k, v);
                        });
                        return builder.build();
                    })
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(json).map(this::parse);
        } catch (Exception e) {
            log.debug("AI service GET {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonNode> postImage(String path, Path file, String contentType, Map<String, String> fields, Duration timeout) {
        if (!enabled) return Optional.empty();
        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(file));
            fields.forEach((k, v) -> {
                if (v != null) parts.add(k, v);
            });
            String json = client(timeout).post().uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(json).map(this::parse);
        } catch (Exception e) {
            log.warn("AI service image check failed, using built-in checks: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("AI service returned invalid JSON", e);
        }
    }
}
