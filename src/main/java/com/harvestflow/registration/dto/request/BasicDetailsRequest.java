package com.harvestflow.registration.dto.request;

import jakarta.validation.constraints.*;

/**
 * First registration step. Farmers often have no email and cannot type a
 * complex password, so:
 * <ul>
 *   <li>username is optional (generated when blank; users log in with their mobile number),</li>
 *   <li>email is optional,</li>
 *   <li>password needs 6+ characters, so a 6-digit PIN works. Login lockout after 5 failures protects it.</li>
 * </ul>
 */
public record BasicDetailsRequest(
        @Size(max = 32) @Pattern(regexp = "^$|^[a-zA-Z0-9._]{4,32}$", message = "4-32 letters, digits, dot or underscore") String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @NotBlank @Size(max = 255) String fullName,
        @Email @Size(max = 255) String email) {
}
