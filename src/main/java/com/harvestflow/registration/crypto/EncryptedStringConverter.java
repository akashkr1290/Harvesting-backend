package com.harvestflow.registration.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AES-256-GCM for Aadhaar and bank account numbers.
 *
 * <p>Keys (base64 of 32 bytes), from environment variables:
 * <ul>
 *   <li>{@code HARVESTFLOW_AADHAAR_ENCRYPTION_KEY}: current key, used for every new write.</li>
 *   <li>{@code HARVESTFLOW_AADHAAR_ENCRYPTION_KEY_OLD}: optional previous key, read-only.</li>
 * </ul>
 * Rows written before a key existed were encrypted with the built-in development
 * key; that key is still tried last when reading so existing users keep working,
 * but it is never used for new writes once a real key is set. In the prod profile
 * {@link EncryptionKeyStartupCheck} refuses to start without a real key.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV = 12;
    static final String ENV_KEY = "HARVESTFLOW_AADHAAR_ENCRYPTION_KEY";
    static final String ENV_OLD_KEY = "HARVESTFLOW_AADHAAR_ENCRYPTION_KEY_OLD";
    /** Development-only key that older deployments used by accident. Read fallback only. */
    private static final String LEGACY_DEV_KEY = "SGFydmVzdEZsb3dEZXZBYWRoYWFyS2V5MzJCeXRlcyE=";
    private static volatile boolean warned;

    @Override
    public String convertToDatabaseColumn(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[IV];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(writeKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV + ct.length];
            System.arraycopy(iv, 0, out, 0, IV);
            System.arraycopy(ct, 0, out, IV, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Sensitive field encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String value) {
        if (value == null) return null;
        byte[] all = Base64.getDecoder().decode(value);
        byte[] iv = new byte[IV];
        System.arraycopy(all, 0, iv, 0, IV);
        byte[] ct = new byte[all.length - IV];
        System.arraycopy(all, IV, ct, 0, ct.length);
        for (byte[] key : readKeys()) {
            try {
                Cipher c = Cipher.getInstance(ALGO);
                c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
                return new String(c.doFinal(ct), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // wrong key: GCM tag check fails, try the next one
            }
        }
        throw new IllegalStateException("Sensitive field decryption failed with every configured key");
    }

    static boolean realKeyConfigured() {
        String v = System.getenv(ENV_KEY);
        return v != null && !v.isBlank();
    }

    private static byte[] writeKey() {
        if (realKeyConfigured()) return decode(System.getenv(ENV_KEY), ENV_KEY);
        if (!warned) {
            warned = true;
            log.warn("{} is not set: encrypting with the built-in development key. Set a real key before storing real Aadhaar or bank data.", ENV_KEY);
        }
        return decode(LEGACY_DEV_KEY, "built-in dev key");
    }

    private static List<byte[]> readKeys() {
        List<byte[]> keys = new ArrayList<>(3);
        if (realKeyConfigured()) keys.add(decode(System.getenv(ENV_KEY), ENV_KEY));
        String old = System.getenv(ENV_OLD_KEY);
        if (old != null && !old.isBlank()) keys.add(decode(old, ENV_OLD_KEY));
        keys.add(decode(LEGACY_DEV_KEY, "built-in dev key"));
        return keys;
    }

    private static byte[] decode(String b64, String name) {
        byte[] k = Base64.getDecoder().decode(b64.trim());
        if (k.length != 32) throw new IllegalStateException(name + " must be base64 of exactly 32 bytes (openssl rand -base64 32)");
        return k;
    }
}
