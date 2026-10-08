package com.harvestflow.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvestflow.api.service.MarketPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/market-prices?crop=Tomato&state=Maharashtra&district=Nashik - any logged-in user. */
@RestController
@RequestMapping("/api/market-prices")
@RequiredArgsConstructor
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    @GetMapping
    public JsonNode latest(@RequestParam String crop,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String district) {
        return marketPriceService.latest(crop, state, district);
    }
}
