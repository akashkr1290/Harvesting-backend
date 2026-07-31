package com.harvestflow.api.exception;

/**
 * Thrown when a PDF document is requested for a stage that hasn't happened
 * yet — e.g. asking for the purchase invoice PDF before Purchase Account
 * has actually generated one. Distinct from ResourceNotFoundException
 * (the case itself exists; this specific derived document just isn't
 * ready), so it maps to 409 rather than 404.
 */
public class DocumentNotReadyException extends RuntimeException {
    public DocumentNotReadyException(String message) {
        super(message);
    }
}
