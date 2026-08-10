package com.fit.fitnessapp.telegram.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkRevocationService;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramLinkController {

    private final CurrentUserApi currentUserApi;
    private final TelegramLinkCodeManager codeManager;
    private final TelegramUserRepository telegramUserRepository;
    private final TelegramLinkRevocationService revocationService;

    @PostMapping("/link-code")
    public ResponseEntity<Map<String, Object>> generateLinkCode() {
        Long userId = currentUserApi.getCurrentUserId();
        String code = codeManager.generateCode(userId);

        return ResponseEntity.ok(Map.of(
                "code", code,
                "expiresInMinutes", 10,
                "instructions", "Send '/link " + code + "' to the Telegram bot in a private chat."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLinkStatus() {
        Long userId = currentUserApi.getCurrentUserId();
        var entityOpt = telegramUserRepository.findByUserId(userId);

        if (entityOpt.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "linked", true,
                    "telegramId", entityOpt.get().getTelegramId(),
                    "linkedAt", entityOpt.get().getLinkedAt()
            ));
        }

        return ResponseEntity.ok(Map.of("linked", false));
    }

    @DeleteMapping("/link")
    public ResponseEntity<Map<String, Object>> unlinkTelegram() {
        Long userId = currentUserApi.getCurrentUserId();
        revocationService.unlink(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Telegram account unlinked successfully"
        ));
    }
}
