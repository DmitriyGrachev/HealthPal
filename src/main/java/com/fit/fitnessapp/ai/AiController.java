package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.auth.CurrentUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final CurrentUserApi currentUserApi;
    private final AiInsightRepository insightRepository;
    private final com.fit.fitnessapp.memory.MemoryUpdateUseCase memoryUpdateUseCase;
    private final FitnessAiService fitnessAiService;


    @GetMapping("/insights/today")
    public ResponseEntity<?> getTodayInsight() {
        Long userId = currentUserApi.getCurrentUserId();
        LocalDate today = LocalDate.now();

        // Ищем именно DAILY инсайт
        Optional<AiInsightEntity> insightOpt = insightRepository.findByUserIdAndDateAndInsightType(userId, today, InsightType.DAILY);

        if (insightOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "Инсайт еще не сгенерирован. Подождите окончания ночной синхронизации или запустите вручную."));
        }

        return ResponseEntity.ok(Map.of(
                "date", today,
                "type", insightOpt.get().getInsightType().name(),
                "insight", insightOpt.get().getInsightText()
        ));
    }
    //TODO
    @PostMapping("/insights/generate")
    public ResponseEntity<?> generateInsight() {
        Long userId = currentUserApi.getCurrentUserId();

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/memory/test")
    public ResponseEntity<String> testMemory(@RequestParam String fact) {
        Long userId = currentUserApi.getCurrentUserId();
        // Manual sync event trigger for testing
        com.fit.fitnessapp.nutrition.domain.NutritionSyncedEvent event = new com.fit.fitnessapp.nutrition.domain.NutritionSyncedEvent(
                userId, LocalDate.now(), 2500, 150.0, 80.0, 300.0
        );

        // Save the fact first
        memoryUpdateUseCase.updateMemory(userId, fact, com.fit.fitnessapp.memory.MemoryType.FACT);

        // Trigger AI analysis
        fitnessAiService.onNutritionSynced(event);

        return ResponseEntity.ok("Memory updated and AI analysis triggered with fact: " + fact);
    }
    }