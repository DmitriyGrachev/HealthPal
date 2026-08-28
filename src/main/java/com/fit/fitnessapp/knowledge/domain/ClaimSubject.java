package com.fit.fitnessapp.knowledge.domain;

public record ClaimSubject(String value) {
    public ClaimSubject {
        value = ClaimTextNormalizer.display(value, "subject", 256);
    }

    public String normalized() {
        return ClaimTextNormalizer.comparison(value);
    }
}
