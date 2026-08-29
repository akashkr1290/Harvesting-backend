package com.harvestflow.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetNotificationService.class);

    public void notifyPasswordReset(String email, String username, String resetToken) {
        log.info("Password reset requested for user '{}' ({}). Reset token: {} " +
                        "— NOTE: no email is actually sent (no SMTP configured); " +
                        "this token is logged here only until real email delivery is wired up.",
                username, email, resetToken);
    }
}
