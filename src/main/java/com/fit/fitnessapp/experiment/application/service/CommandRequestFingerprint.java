package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.domain.Goal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Builds deterministic, privacy-safe command digests. Raw request values never leave this class. */
final class CommandRequestFingerprint {
    private CommandRequestFingerprint() { }

    static String investigationCreate(String title, String problemStatement) {
        return digest("INVESTIGATION_CREATE", title, problemStatement);
    }

    static String investigationTransition(Long aggregateId, String command, long expectedVersion, String reason) {
        return digest("INVESTIGATION_TRANSITION", aggregateId, command, expectedVersion, reason);
    }

    static String goalCreate(Goal goal) {
        return digest("GOAL_CREATE", goal.type(), goal.name(), goal.metric(),
                goal.targetRange() == null ? null : goal.targetRange().minimum(),
                goal.targetRange() == null ? null : goal.targetRange().maximum(),
                goal.targetRange() == null ? null : goal.targetRange().unit(), goal.deadline(), goal.priority(),
                goal.source(), goal.investigationId(), goal.supersededGoalId(), goal.primary());
    }

    static String goalTransition(Long aggregateId, String command, long expectedVersion, String reason) {
        return digest("GOAL_TRANSITION", aggregateId, command, expectedVersion, reason);
    }

    private static String digest(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : String.valueOf(value);
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
