package com.fit.fitnessapp.memory;

public enum MemoryType {
    FACT,       // Long-term facts (allergies, goals)
    SEMANTIC,   // Extracted patterns (higher calories on Fridays)
    EPISODIC,   // Specific events or notes from user
    KNOWLEDGE   // Scientific evidence or general knowledge (RAG)
}
