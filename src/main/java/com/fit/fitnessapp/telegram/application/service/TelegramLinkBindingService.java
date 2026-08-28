package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Owns the persistence details of binding one Telegram identity to one FitnessApp owner. */
@Service
public class TelegramLinkBindingService {

    private final TelegramLinkCodeManager codes;
    private final TelegramUserRepository users;
    private final Clock clock;

    public TelegramLinkBindingService(
            TelegramLinkCodeManager codes,
            TelegramUserRepository users,
            Clock clock) {
        this.codes = codes;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public LinkResult link(Long senderId, Long chatId, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return new LinkResult(LinkStatus.CODE_REQUIRED, null);
        }
        if (codes.isLinkAttemptLocked(chatId)) {
            return new LinkResult(LinkStatus.RATE_LIMITED, null);
        }
        var userId = codes.consumeCode(rawCode.trim());
        if (userId.isEmpty()) {
            codes.recordInvalidLinkAttempt(chatId);
            LinkStatus status = codes.isLinkAttemptLocked(chatId)
                    ? LinkStatus.RATE_LIMITED
                    : LinkStatus.INVALID;
            return new LinkResult(status, null);
        }

        users.save(TelegramUserEntity.builder()
                .telegramId(senderId)
                .userId(userId.get())
                .chatId(chatId)
                .linkedAt(OffsetDateTime.now(clock))
                .build());
        codes.clearInvalidLinkAttempts(chatId);
        return new LinkResult(LinkStatus.LINKED, userId.get());
    }

    public record LinkResult(LinkStatus status, Long userId) {
        public LinkResult {
            if (status == null || status == LinkStatus.LINKED && userId == null) {
                throw new IllegalArgumentException("link result is incomplete");
            }
        }
    }

    public enum LinkStatus {
        LINKED,
        CODE_REQUIRED,
        INVALID,
        RATE_LIMITED
    }
}
