package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.memory.application.port.in.MemoryQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiContextService {

    private final MemoryQueryUseCase memoryQueryUseCase;
    private final AiInsightRepository insightRepository;

    public String buildMemoryContext(Long userId, String semanticQuery) {
        StringBuilder sb = new StringBuilder();

        var facts = memoryQueryUseCase.findLongTermFacts(userId, 5);
        if (!facts.isEmpty()) {
            sb.append("PERMANENT USER FACTS:\n");
            facts.forEach(m -> sb.append("- ").append(m.content()).append("\n"));
        }

        var patterns = memoryQueryUseCase.findRelevantMemories(userId, semanticQuery, 3);
        if (!patterns.isEmpty()) {
            sb.append("\nPATTERNS AND HISTORY:\n");
            patterns.forEach(m -> sb.append("- ").append(m.content()).append("\n"));
        }

        var recentContext = memoryQueryUseCase.findRecentContext(userId, 7, 3);
        if (!recentContext.isEmpty()) {
            sb.append("\nCURRENT CONTEXT (last 7 days):\n");
            recentContext.forEach(m -> sb.append("- ").append(m.content()).append("\n"));
        }

        return sb.length() > 0 ? sb.toString() : "No user data.";
    }

    public String getRecentInsightsSummary(Long userId, InsightType currentType) {
        List<AiInsightEntity> result = new ArrayList<>();

        switch (currentType) {
            case DAILY -> result.addAll(insightRepository
                    .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.DAILY, 3));
            case WEEKLY -> {
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.WEEKLY, 2));
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.DAILY, 3));
            }
            case MONTHLY -> {
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.MONTHLY, 1));
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.WEEKLY, 2));
            }
        }

        if (result.isEmpty()) {
            return "No previous insights.";
        }

        return result.stream()
                .sorted(Comparator.comparing(AiInsightEntity::getDate).reversed())
                .map(i -> String.format("[%s %s] %s",
                        i.getInsightType(), i.getDate(),
                        i.getInsightText().length() > 150
                                ? i.getInsightText().substring(0, 150) + "..."
                                : i.getInsightText()))
                .collect(Collectors.joining("\n"));
    }
}
