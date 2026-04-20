package com.fit.fitnessapp.memory.infrastructure.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class MemoryConfig {

    @Bean
    public VectorStore vectorStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(2048)//pgvector max 2000, decided to go with it jor now
                .vectorTableName("user_memory")
                .initializeSchema(false)
                .build();
    }
}
