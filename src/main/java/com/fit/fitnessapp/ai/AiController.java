package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.security.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final FitnessAiService fitnessAiService;
    private final CurrentUserService currentUserService;

    public AiController(FitnessAiService fitnessAiService, CurrentUserService currentUserService) {
        this.fitnessAiService = fitnessAiService;
        this.currentUserService = currentUserService;
    }

    /*@PostMapping("/ask")
    public ResponseEntity<AiResponseDto> askTrainer(@RequestBody AiRequestDto request) {
        // 1. Get authenticated user ID securely
        Long userId = currentUserService.getCurrentUserId();

        // 2. Call Service
        String answer = fitnessAiService.getPersonalizedAdvice(userId, request.question());

        // 3. Return Response
        return ResponseEntity.ok(new AiResponseDto(answer));
    }
    @GetMapping("/ai/insights")
    public String getInsights(@RequestParam String query) {
        Long userId = currentUserService.getCurrentUserId();

        return chatClient.prompt()
                .user(query)
                .system("Ты персональный фитнес-коуч. Отвечай на русском, коротко и мотивирующе.")
                .call()
                .content();
    }

     */

    // Simple DTOs (Java 17 Records)
    public record AiRequestDto(String question) {}
    public record AiResponseDto(String answer) {}
}

