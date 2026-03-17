package com.fit.fitnessapp.nutrition.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class FatSecretConfigChecker implements CommandLineRunner {

    @Value("${fatsecret.consumer-key}")
    private String consumerKey;

    @Value("${fatsecret.consumer-secret}")
    private String consumerSecret;

    @Value("${fatsecret.callback-url}")
    private String callbackUrl;

    @Override
    public void run(String... args) {
        System.out.println("=".repeat(60));
        System.out.println("FatSecret Configuration Check");
        System.out.println("=".repeat(60));

        System.out.println("Consumer Key: " + maskKey(consumerKey));
        System.out.println("Consumer Secret: " + maskKey(consumerSecret));
        System.out.println("Callback URL: " + callbackUrl);

        // Validation
        boolean valid = true;

        if (consumerKey == null || consumerKey.trim().isEmpty()) {
            System.err.println("❌ Consumer Key is missing!");
            valid = false;
        } else if (consumerKey.contains("YOUR_") || consumerKey.length() < 10) {
            System.err.println("❌ Consumer Key looks invalid (placeholder or too short)");
            valid = false;
        }

        if (consumerSecret == null || consumerSecret.trim().isEmpty()) {
            System.err.println("❌ Consumer Secret is missing!");
            valid = false;
        } else if (consumerSecret.contains("YOUR_") || consumerSecret.length() < 10) {
            System.err.println("❌ Consumer Secret looks invalid (placeholder or too short)");
            valid = false;
        }

        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            System.err.println("❌ Callback URL is missing!");
            valid = false;
        } else if (!callbackUrl.startsWith("http://") && !callbackUrl.startsWith("https://")) {
            System.err.println("❌ Callback URL must start with http:// or https://");
            valid = false;
        }

        if (valid) {
            System.out.println("✅ Configuration looks good!");
        } else {
            System.err.println("\n⚠️  Please check your application.properties or application.yml");
        }

        System.out.println("=".repeat(60));
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) {
            return "***INVALID***";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}