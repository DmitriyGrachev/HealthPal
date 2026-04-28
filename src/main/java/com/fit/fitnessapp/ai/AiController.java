package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.api.InsightType;
import com.fit.fitnessapp.auth.CurrentUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

        AiInsightEntity insight = insightOpt.get();
        return ResponseEntity.ok(Map.of(
                "date", today,
                "type", insight.getInsightType().name(),
                "summary", insight.getInsightText(),
                "structured", insight.getStructuredResponse() != null ? insight.getStructuredResponse() : Map.of()
        ));
    }

    @PostMapping("/insights/generate")
    public ResponseEntity<?> generateInsight(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long userId = currentUserApi.getCurrentUserId();
        LocalDate targetDate = date != null ? date : LocalDate.now();

        fitnessAiService.generateDailyInsight(userId, targetDate);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Генерация инсайта запущена для даты " + targetDate,
                "userId", userId,
                "date", targetDate
        ));
    }
}
