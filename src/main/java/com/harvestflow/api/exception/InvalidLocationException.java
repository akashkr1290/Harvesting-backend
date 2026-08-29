package com.harvestflow.api.exception;

/** Thrown when a route stop's coordinates are missing, out of range, or can't be resolved from a name. */
public class InvalidLocationException extends RuntimeException {
    public InvalidLocationException(String message) {
        super(message);
    }
}
