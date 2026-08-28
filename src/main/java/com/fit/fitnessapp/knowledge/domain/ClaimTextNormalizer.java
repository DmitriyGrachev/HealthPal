package com.fit.fitnessapp.knowledge.domain;

import java.text.Normalizer;
import java.util.Locale;

final class ClaimTextNormalizer {
    private ClaimTextNormalizer() {
    }

    static String display(String value, String field, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be between 1 and " + maxLength + " characters");
        }
        return normalized;
    }

    static String comparison(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    static String stableIdentifier(String value, String field) {
        String identifier = display(value, field, 200);
        if (!identifier.matches("[A-Za-z0-9][A-Za-z0-9._:@/\\-]{0,199}")) {
            throw new IllegalArgumentException(field + " must be a stable identifier");
        }
        return identifier;
    }

    static String stableType(String value, String field) {
        String type = display(value, field, 64).toUpperCase(Locale.ROOT);
        if (!type.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException(field + " must be a stable type code");
        }
        return type;
    }
}
