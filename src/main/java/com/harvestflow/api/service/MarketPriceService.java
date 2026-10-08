package com.harvestflow.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.service.ai.AiServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Today's mandi (APMC) price for a crop, from Agmarknet through the AI service.
 * Shown next to every offer so farmers see whether HarvestFlow beats the mandi.
 * Never invents a price: if there is no data, the caller gets 404 and the app
 * hides the comparison.
 */
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final AiServiceClient ai;

    public JsonNode latest(String crop, String state, String district) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("crop", crop);
        q.put("state", state);
        q.put("district", district);
        return ai.get("/market/mandi-price", q, TIMEOUT)
                .orElseThrow(() -> new ResourceNotFoundException("No mandi price available for " + crop + "."));
    }
}
