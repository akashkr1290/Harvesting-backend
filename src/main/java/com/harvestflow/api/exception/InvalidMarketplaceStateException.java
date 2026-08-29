package com.harvestflow.api.exception;

/**
 * Thrown for any marketplace action attempted against an entity in the
 * wrong state — offering on a closed listing, accepting an already-decided
 * offer, publishing an already-published listing, etc. Mirrors
 * {@link InvalidTransitionException}'s role for the harvest-case state
 * machine, kept separate since the two domains don't share a lifecycle.
 */
public class InvalidMarketplaceStateException extends RuntimeException {
    public InvalidMarketplaceStateException(String message) {
        super(message);
    }
}
