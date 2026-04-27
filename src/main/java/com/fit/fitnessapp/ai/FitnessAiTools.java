package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.memory.domain.MemoryType;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class FitnessAiTools {

    public record SaveMemoryRequest(Long userId, String content, String type) {}

    @Bean
    @Description("Save an important long-term fact or behavioral pattern about the user. " +
            "Use for food intolerances, training preferences, stable weekly patterns. " +
            "Type: FACT for permanent facts, SEMANTIC for discovered patterns, EPISODIC for specific events.")
    public Function<SaveMemoryRequest, String> saveMemory(VectorStore vectorStore) {
        return request -> {
            if (request.userId() == null || request.content() == null) {
                return "Error: userId and content are required.";
            }

            String memoryType;
            try {
                MemoryType.valueOf(request.type().toUpperCase());
                memoryType = request.type().toUpperCase();
            } catch (Exception e) {
                memoryType = MemoryType.FACT.name();
            }

            Document doc = new Document(
                    request.content(),
                    Map.of(
                            "user_id", request.userId(),
                            "memory_type", memoryType,
                            "created_at", Instant.now().toString()
                    )
            );

            vectorStore.add(List.of(doc));
            return "Memory saved for user " + request.userId() + ": " + request.content();
        };
    }
}