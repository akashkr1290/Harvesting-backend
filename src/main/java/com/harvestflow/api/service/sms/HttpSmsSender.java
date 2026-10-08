package com.harvestflow.api.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Generic HTTP SMS gateway. Most Indian SMS providers accept a GET or POST
 * with the number and text as parameters, so the request is described by
 * configuration rather than code:
 *
 * <pre>
 * SMS_PROVIDER=http
 * SMS_HTTP_URL=https://gateway.example/send?to={mobile}&amp;text={message}
 * SMS_HTTP_METHOD=GET              # or POST
 * SMS_HTTP_BODY=                   # POST body template, same placeholders
 * SMS_HTTP_AUTH_HEADER=authorization
 * SMS_HTTP_AUTH_VALUE=&lt;your API key&gt;
 * </pre>
 *
 * {mobile} is the 10-digit number, {mobile91} is 91 followed by it, and
 * {message} is the URL-encoded text. DLT template rules in India still
 * apply: register the OTP and order-update texts with your provider.
 */
@Service
@ConditionalOnProperty(name = "harvestflow.sms.provider", havingValue = "http")
public class HttpSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsSender.class);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String urlTemplate;
    private final String method;
    private final String bodyTemplate;
    private final String authHeader;
    private final String authValue;

    public HttpSmsSender(@Value("${harvestflow.sms.http.url}") String urlTemplate,
                         @Value("${harvestflow.sms.http.method:GET}") String method,
                         @Value("${harvestflow.sms.http.body:}") String bodyTemplate,
                         @Value("${harvestflow.sms.http.auth-header:}") String authHeader,
                         @Value("${harvestflow.sms.http.auth-value:}") String authValue) {
        this.urlTemplate = urlTemplate;
        this.method = method == null ? "GET" : method.trim().toUpperCase();
        this.bodyTemplate = bodyTemplate;
        this.authHeader = authHeader;
        this.authValue = authValue;
    }

    @Override
    public boolean send(String mobile, String message) {
        if (mobile == null || !mobile.matches("\\d{10}")) {
            log.warn("SMS skipped: invalid mobile {}", LogSmsSender.mask(mobile));
            return false;
        }
        try {
            String url = fill(urlTemplate, mobile, message, true);
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10));
            if (authHeader != null && !authHeader.isBlank()) {
                req.header(authHeader, authValue == null ? "" : authValue);
            }
            if ("POST".equals(method)) {
                String template = bodyTemplate == null ? "" : bodyTemplate;
                boolean json = template.trim().startsWith("{");
                String body = fill(template, mobile, message, !json);
                String type = json ? "application/json" : "application/x-www-form-urlencoded";
                req.header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                req.GET();
            }
            HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() >= 200 && res.statusCode() < 300;
            if (!ok) {
                log.warn("SMS gateway returned HTTP {} for {}", res.statusCode(), LogSmsSender.mask(mobile));
            }
            return ok;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("SMS sending failed for {}: {}", LogSmsSender.mask(mobile), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deliversToPhone() {
        return true;
    }

    private static String fill(String template, String mobile, String message, boolean urlEncode) {
        String text = urlEncode ? URLEncoder.encode(message, StandardCharsets.UTF_8) : jsonSafe(message);
        return template.replace("{mobile91}", "91" + mobile).replace("{mobile}", mobile).replace("{message}", text);
    }

    private static String jsonSafe(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
