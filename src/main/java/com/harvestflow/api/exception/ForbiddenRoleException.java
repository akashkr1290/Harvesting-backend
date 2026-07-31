package com.harvestflow.api.exception;

/**
 * Thrown when a user attempts an action their role doesn't own — e.g. a
 * Supervisor trying to save a Planning form, or a Plot Selection user
 * trying to edit a case after Planning has already started. This is the
 * server-side enforcement the Flutter prototype flagged as still missing
 * (client-only "view only" flags are not real access control).
 */
public class ForbiddenRoleException extends RuntimeException {
    public ForbiddenRoleException(String message) {
        super(message);
    }
}
