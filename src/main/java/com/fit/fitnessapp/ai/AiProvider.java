package com.fit.fitnessapp.ai;

public enum AiProvider {
    OPENROUTER("openrouter"),
    GOOGLE("google");

    private final String key;

    AiProvider(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static AiProvider fromKey(String key) {
        for (AiProvider p : values()) {
            if (p.key.equalsIgnoreCase(key)) return p;
        }
        throw new IllegalArgumentException("Unknown AI provider: " + key);
    }
}
