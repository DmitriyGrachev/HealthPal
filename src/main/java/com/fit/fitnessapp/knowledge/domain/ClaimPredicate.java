package com.fit.fitnessapp.knowledge.domain;

public record ClaimPredicate(String value) {
    public ClaimPredicate {
        value = ClaimTextNormalizer.display(value, "predicate", 128);
    }

    public String normalized() {
        return ClaimTextNormalizer.comparison(value);
    }
}
