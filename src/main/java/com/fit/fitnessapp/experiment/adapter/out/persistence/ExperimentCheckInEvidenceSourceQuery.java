package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.api.evidence.EvidenceSourceQuery;
import com.fit.fitnessapp.api.evidence.EvidenceSourceRequest;
import com.fit.fitnessapp.api.evidence.EvidenceSourceSlice;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.domain.ContextRating;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Maps immutable manual check-ins to content-free, reproducible evidence identities. */
@Component
public class ExperimentCheckInEvidenceSourceQuery implements EvidenceSourceQuery {

    private static final String SOURCE_TYPE = "MANUAL_CHECK_IN";

    private final EvidenceRepositoryPort evidence;

    public ExperimentCheckInEvidenceSourceQuery(EvidenceRepositoryPort evidence) {
        this.evidence = evidence;
    }

    @Override
    public EvidenceSourceSlice query(EvidenceSourceRequest request) {
        return new EvidenceSourceSlice(SOURCE_TYPE, evidence
                .findCheckInsByUserIdAndExperimentIdAndLocalDateBetween(
                        request.userId(), request.subjectId(),
                        request.fromInclusive(), request.toInclusive())
                .stream()
                .map(checkIn -> new EvidenceSourceSlice.EvidenceItem(
                        checkIn.id().toString(),
                        1,
                        contentHash(checkIn),
                        checkIn.localDate(),
                        checkIn.recordedAt()))
                .toList());
    }

    private static String contentHash(ExperimentCheckIn checkIn) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, checkIn.id());
        append(canonical, checkIn.experimentId());
        append(canonical, checkIn.localDate());
        append(canonical, checkIn.timezone().getId());
        append(canonical, checkIn.scheduledStartAt());
        append(canonical, checkIn.scheduledEndAt());
        append(canonical, checkIn.adherence().name());
        append(canonical, checkIn.adherenceValue());
        append(canonical, checkIn.deviationReason());
        append(canonical, checkIn.note());
        append(canonical, rating(checkIn.readiness()));
        append(canonical, rating(checkIn.sleep()));
        append(canonical, rating(checkIn.mood()));
        append(canonical, checkIn.source().name());
        append(canonical, checkIn.recordedAt());
        append(canonical, checkIn.createdAt());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Integer rating(ContextRating rating) {
        return rating == null ? null : rating.value();
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text).append(';');
    }
}
