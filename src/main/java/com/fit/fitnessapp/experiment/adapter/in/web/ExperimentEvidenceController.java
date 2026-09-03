package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCheckInUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentDecisionUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentEvaluationUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentOutcomeUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ContextRating;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/experiments/{experimentId}")
@Validated
@RequiredArgsConstructor
public class ExperimentEvidenceController {
    private final ExperimentCheckInUseCase checkIns;
    private final ExperimentOutcomeUseCase outcomes;
    private final ExperimentEvaluationUseCase evaluations;
    private final ExperimentDecisionUseCase decisions;
    private final CurrentUserApi currentUserApi;

    @PostMapping("/check-ins")
    public ResponseEntity<ExperimentCheckInResponse> checkIn(
            @PathVariable @Positive Long experimentId,
            @Valid @RequestBody ExperimentCheckInRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        String key = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        Instant now = Instant.now(Clock.systemUTC());
        CheckInSource source = request.source() == null ? CheckInSource.MANUAL : request.source();
        ExperimentCheckIn checkIn = new ExperimentCheckIn(
                null, userId, experimentId, request.localDate(), zoneId(request.timezone()),
                request.scheduledStartAt(), request.scheduledEndAt(), request.adherence(),
                request.adherenceValue(), request.deviationReason(), request.note(),
                rating(request.readiness()), rating(request.sleep()), rating(request.mood()),
                source, now, now);
        EvidenceCommandResult<com.fit.fitnessapp.experiment.domain.ExperimentCheckIn> result =
                checkIns.recordWithStatus(userId, experimentId, checkIn, key);
        ExperimentCheckInResponse response = ExperimentCheckInResponse.from(result.value());
        return response(result.created(), "/api/v1/experiments/" + experimentId
                + "/check-ins/" + response.id(), response);
    }

    @PostMapping("/outcomes")
    public ResponseEntity<ExperimentOutcomeResponse> outcome(
            @PathVariable @Positive Long experimentId,
            @Valid @RequestBody ExperimentOutcomeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        String key = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        Instant now = Instant.now(Clock.systemUTC());
        OutcomeSource source = request.source() == null ? OutcomeSource.MANUAL : request.source();
        Outcome outcome = new Outcome(null, userId, experimentId, request.metricKey(), request.baselineValue(),
                request.observedValue(), request.unit(), request.baselineSampleCount(),
                request.observedSampleCount(), request.observedAt(), source, request.note(), now);
        EvidenceCommandResult<com.fit.fitnessapp.experiment.domain.Outcome> result =
                outcomes.recordWithStatus(userId, experimentId, outcome, key);
        ExperimentOutcomeResponse response = ExperimentOutcomeResponse.from(result.value());
        return response(result.created(), "/api/v1/experiments/" + experimentId
                + "/outcomes/" + response.id(), response);
    }

    @PostMapping("/evaluation")
    public ResponseEntity<ExperimentEvaluationResponse> evaluation(
            @PathVariable @Positive Long experimentId,
            @Valid @RequestBody ExperimentEvaluationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        String key = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        EvidenceCommandResult<com.fit.fitnessapp.experiment.domain.Evaluation> result =
                evaluations.evaluateWithStatus(userId, experimentId, request.expectedVersion(), key);
        ExperimentEvaluationResponse response = ExperimentEvaluationResponse.from(result.value());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/decision")
    public ResponseEntity<ExperimentDecisionResponse> decision(
            @PathVariable @Positive Long experimentId,
            @Valid @RequestBody ExperimentDecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        String key = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        EvidenceCommandResult<com.fit.fitnessapp.experiment.domain.UserDecision> result =
                decisions.decideWithStatus(userId, experimentId, request.evaluationId(), request.decision(),
                        request.note(), request.claimsUsed(), key);
        ExperimentDecisionResponse response = ExperimentDecisionResponse.from(result.value());
        return response(result.created(), "/api/v1/experiments/" + experimentId
                + "/decision/" + response.id(), response);
    }

    private static ContextRating rating(Integer value) {
        return value == null ? null : new ContextRating(value);
    }

    private static <T> ResponseEntity<T> response(boolean created, String location, T body) {
        ResponseEntity.BodyBuilder builder = created
                ? ResponseEntity.created(URI.create(location))
                : ResponseEntity.ok();
        return builder.body(body);
    }

    private static ZoneId zoneId(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timezone is invalid");
        }
    }

    private static String resolveIdempotencyKey(String bodyKey, String headerKey) {
        boolean bodyPresent = bodyKey != null && !bodyKey.isBlank();
        boolean headerPresent = headerKey != null && !headerKey.isBlank();
        if (bodyPresent && headerPresent && !bodyKey.equals(headerKey)) {
            throw new IdempotencyConflictException();
        }
        if (!bodyPresent && !headerPresent) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
        return headerPresent ? headerKey : bodyKey;
    }
}
