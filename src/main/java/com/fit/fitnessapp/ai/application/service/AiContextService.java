package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.ai.application.port.out.AiInsightPort;
import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.knowledge.context.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AiContextService {

    private final UserContextQuery userContextQuery;
    private final AiInsightPort insightRepository;
    private final AiSafetyService aiSafetyService;
    private final SensitiveAiEgressGuard egressGuard;
    private final Clock clock;

    /** Current supplementary context; report-period measurements come from the report's own snapshot. */
    public String buildMemoryContext(Long userId, String semanticQuery) {
        return prepareTelegramContext(userId).text();
    }

    public PreparedContext prepareTelegramContext(Long userId) {
        egressGuard.validateSensitiveEgress();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var context = (UserContext.TelegramAnswer) userContextQuery.assemble(new UserContextRequest(userId,
                ContextPurpose.TELEGRAM_ANSWER, today.minusDays(29), today, null, null, 3, 2_000));
        var available = new ArrayList<ContextSlices.Claim>();
        var text = new StringBuilder("PURPOSE-SCOPED CONTEXT (as of " + context.metadata().asOf() + "):\n");
        appendClaims(text, "VERIFIED CONSTRAINTS", context.verifiedConstraints(), available);
        appendClaims(text, "SUPPORTED CLAIMS (not permanent truths)", context.facts(), available);
        appendClaims(text, "UNCONFIRMED NARRATIVES (hypotheses only)", context.narratives(), available);
        context.goals().forEach(goal -> appendMemory(text, "GOAL: " + goal));
        text.append("OBSERVATION IDENTITIES: ").append(context.observations()).append('\n');
        text.append("MISSING CONTEXT: ").append(context.metadata().missing()).append('\n');
        boolean warning = context.metadata().rejectedClaims().stream()
                .anyMatch(rejected -> rejected.reason().equals("OPEN_CONFLICT") || rejected.reason().equals("DISPUTED"));
        if (warning) text.append("CONTEXT CONFLICT: conflicting/disputed claims were excluded; do not resolve them by guessing.\n");
        return new PreparedContext(text.toString(), available, warning);
    }

    private void appendClaims(StringBuilder text, String label, List<ContextSlices.Claim> claims,
                              List<ContextSlices.Claim> available) {
        text.append(label).append(":\n");
        for (var claim : claims) {
            text.append("CLAIM_ID=").append(claim.id()).append(" VERSION=").append(claim.version())
                    .append(" VERIFICATION=").append(claim.verification()).append('\n');
            appendMemory(text, claim.subject() + " / " + claim.predicate() + " = " + claim.value()
                    + (claim.unit() == null ? "" : " " + claim.unit()));
            available.add(claim);
        }
    }

    public record PreparedContext(String text, List<ContextSlices.Claim> claims, boolean conflictWarning) {
        public PreparedContext { claims = List.copyOf(claims); }

        public List<ClaimUseReference> referencesFor(List<Long> citedIds) {
            if (citedIds == null || citedIds.size() > 20) throw new IllegalArgumentException("invalid Claim citations");
            var references = citedIds.stream().map(id -> {
                var claim = claims.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("AI cited an unavailable Claim"));
                return new ClaimUseReference(claim.id(), claim.version(), claim.source().contentHash());
            }).toList();
            return ClaimUseReference.canonicalize(references);
        }
    }

    private void appendMemory(StringBuilder context, String content) {
        context.append("- ")
                .append(aiSafetyService.wrapUntrusted(AiSafetyService.UntrustedDataType.USER_MEMORY, content))
                .append('\n');
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
