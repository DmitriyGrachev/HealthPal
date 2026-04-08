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
}