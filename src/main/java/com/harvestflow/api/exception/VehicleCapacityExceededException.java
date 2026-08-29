package com.harvestflow.api.exception;

/** Thrown when the requested load exceeds the selected vehicle's capacity. */
public class VehicleCapacityExceededException extends RuntimeException {
    public VehicleCapacityExceededException(String message) {
        super(message);
    }
}
