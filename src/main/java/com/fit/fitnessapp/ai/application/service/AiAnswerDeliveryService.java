package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.delivery.OwnedMessageOutbox;
import com.fit.fitnessapp.knowledge.context.AnswerClaimUsage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Commits the queued answer and exact declared Claim usage together, with no external I/O. */
@Service
public class AiAnswerDeliveryService {
    public static final String CONFLICT_WARNING = "В контексте есть спорные или противоречивые сведения; уточните их, прежде чем опираться на ответ.\n";
    public static final String UNCONFIRMED_WARNING = "Упомянутые неподтверждённые сведения — гипотезы, а не установленные факты.\n";
    private final OwnedMessageOutbox outbox;
    private final AnswerClaimUsage usage;

    public AiAnswerDeliveryService(OwnedMessageOutbox outbox, AnswerClaimUsage usage) {
        this.outbox = outbox;
        this.usage = usage;
    }

    @Transactional
    public boolean deliver(Long userId, Long chatId, String summary, AiContextService.PreparedContext context,
                           List<Long> citedIds) {
        if (summary == null || summary.isBlank() || summary.length() > 8_000) throw new IllegalArgumentException("invalid AI answer");
        var references = context.referencesFor(citedIds);
        var warnings = usage.validateAndLock(userId, references);
        String answer = (context.conflictWarning() || warnings.conflict() ? CONFLICT_WARNING : "")
                + (warnings.unconfirmedHypothesis() ? UNCONFIRMED_WARNING : "") + summary;
        var deliveries = outbox.enqueue(userId, chatId, answer);
        if (deliveries.isEmpty()) return false;
        usage.record(userId, "telegram-answer:" + deliveries.getFirst(), references);
        return true;
    }
}
