package com.harvestflow.api.exception;

/** Thrown when a route cannot be computed at all (e.g. no valid stops remain, or distance computation fails). */
public class RouteImpossibleException extends RuntimeException {
    public RouteImpossibleException(String message) {
        super(message);
    }
}
