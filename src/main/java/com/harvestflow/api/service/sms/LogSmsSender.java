package com.harvestflow.api.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development sender: writes the message to the server log and sends
 * nothing. The mobile number is masked so logs never hold full numbers.
 */
@Service
@ConditionalOnProperty(name = "harvestflow.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public boolean send(String mobile, String message) {
        log.info("[SMS not sent - log provider] to {}: {}", mask(mobile), message);
        return true;
    }

    @Override
    public boolean deliversToPhone() {
        return false;
    }

    static String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return "XXXX";
        return "XXXXXX" + mobile.substring(mobile.length() - 4);
    }
}
