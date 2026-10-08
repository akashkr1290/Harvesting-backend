package com.harvestflow.registration.crypto;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Production must never write Aadhaar or bank numbers with the public development key. */
@Component
@Profile("prod")
public class EncryptionKeyStartupCheck implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        if (!EncryptedStringConverter.realKeyConfigured()) {
            throw new IllegalStateException(EncryptedStringConverter.ENV_KEY
                    + " must be set in production. Generate one with: openssl rand -base64 32");
        }
    }
}
