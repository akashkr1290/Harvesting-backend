package com.harvestflow.api.exception;

/**
 * Thrown when a demand forecast is requested for a crop/location that
 * doesn't yet have enough completed-harvest history to forecast from.
 * Maps to 422 — the request is well-formed, the data just isn't there yet.
 */
public class InsufficientDataException extends RuntimeException {
    public InsufficientDataException(String message) {
        super(message);
    }
}
