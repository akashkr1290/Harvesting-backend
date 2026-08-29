package com.harvestflow.api.exception;

/**
 * Thrown when the forecasting/optimization computation itself fails
 * unexpectedly (as opposed to bad input or missing data). Kept distinct
 * from a generic 500 so the frontend can show "forecasting is temporarily
 * unavailable, try again" rather than a bare error.
 */
public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
