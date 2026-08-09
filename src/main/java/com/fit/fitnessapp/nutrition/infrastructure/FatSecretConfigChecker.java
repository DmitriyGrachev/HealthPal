package com.fit.fitnessapp.nutrition.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "fatsecret.config-check", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FatSecretConfigChecker implements CommandLineRunner {

    private final String consumerKey;
    private final String consumerSecret;
    private final String callbackUrl;

    public FatSecretConfigChecker(
            @Value("${fatsecret.consumer-key}") String consumerKey,
            @Value("${fatsecret.consumer-secret}") String consumerSecret,
            @Value("${fatsecret.callback-url}") String callbackUrl) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public void run(String... args) {
        boolean valid = true;

        if (consumerKey == null || consumerKey.trim().isEmpty()) {
            log.warn("FatSecret consumer key is missing");
            valid = false;
        } else if (consumerKey.contains("YOUR_") || consumerKey.length() < 10) {
            log.warn("FatSecret consumer key looks invalid");
            valid = false;
        }

        if (consumerSecret == null || consumerSecret.trim().isEmpty()) {
            log.warn("FatSecret consumer secret is missing");
            valid = false;
        } else if (consumerSecret.contains("YOUR_") || consumerSecret.length() < 10) {
            log.warn("FatSecret consumer secret looks invalid");
            valid = false;
        }

        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            log.warn("FatSecret callback URL is missing");
            valid = false;
        } else if (!callbackUrl.startsWith("http://") && !callbackUrl.startsWith("https://")) {
            log.warn("FatSecret callback URL must start with http:// or https://");
            valid = false;
        }

        if (valid) {
            log.info("FatSecret configuration is valid. Consumer key: {}, callback URL: {}", maskKey(consumerKey), callbackUrl);
        } else {
            log.warn("FatSecret configuration is invalid. Check application properties.");
        }
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) {
            return "***INVALID***";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
