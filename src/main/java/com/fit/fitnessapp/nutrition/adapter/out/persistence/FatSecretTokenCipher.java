package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class FatSecretTokenCipher {
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String TEST_PROFILE_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String MISSING_KEY_MESSAGE =
            "fatsecret.token-encryption.key is required; set FATSECRET_TOKEN_ENCRYPTION_KEY to a base64 AES key";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public FatSecretTokenCipher(
            @Value("${fatsecret.token-encryption.key:}") String encodedKey,
            Environment environment) {
        String keyMaterial = StringUtils.hasText(encodedKey)
                ? encodedKey
                : testKeyForTestProfile(environment);
        this.keySpec = new SecretKeySpec(decodeKey(keyMaterial), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt FatSecret token", exception);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || !storedValue.startsWith(PREFIX)) {
            return storedValue;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted payload is too short");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt FatSecret token", exception);
        }
    }

    private String testKeyForTestProfile(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return TEST_PROFILE_KEY;
        }
        throw new IllegalStateException(MISSING_KEY_MESSAGE);
    }

    private byte[] decodeKey(String encodedKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length == 16 || key.length == 24 || key.length == 32) {
                return key;
            }
            throw new IllegalStateException(
                    "fatsecret.token-encryption.key must decode to a 16, 24, or 32 byte AES key");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("fatsecret.token-encryption.key must be valid base64", exception);
        }
    }
}
