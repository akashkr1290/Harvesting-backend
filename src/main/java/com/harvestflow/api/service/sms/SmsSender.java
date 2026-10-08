package com.harvestflow.api.service.sms;

/**
 * Sends a text message to an Indian mobile number (10 digits, no +91).
 * Farmers rarely open an app, so OTPs and important order updates go out
 * by SMS as well as in-app. Choose the implementation with
 * {@code harvestflow.sms.provider}: {@code log} (default, prints to the
 * server log only) or {@code http} (any SMS gateway with an HTTP API).
 */
public interface SmsSender {

    /** @return true when the gateway accepted the message. Never throws. */
    boolean send(String mobile, String message);

    /** false for the log-only sender: callers must not assume the user received anything. */
    boolean deliversToPhone();
}
