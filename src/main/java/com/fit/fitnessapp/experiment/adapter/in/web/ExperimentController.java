package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentQueryUseCase;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/experiments")
@Validated
@RequiredArgsConstructor
public class ExperimentController {
    private final ExperimentCommandUseCase commands;
    private final ExperimentQueryUseCase queries;
    private final CurrentUserApi currentUserApi;

    @PostMapping
    public ResponseEntity<ExperimentResponse> create(
            @Valid @RequestBody ExperimentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @jakarta.validation.constraints.Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        String idempotencyKey = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        Experiment experiment = Experiment.create(
                userId,
                request.investigationId(),
                request.goalId(),
                new Hypothesis(request.hypothesis()),
                request.baselineStartDate(),
                request.baselineEndDate(),
                request.durationDays(),
                new Intervention(request.intervention().action(), request.intervention().protocol()),
                request.primaryMetric(),
                request.secondaryMetrics() == null ? List.of() : request.secondaryMetrics(),
                request.stopConditions().stream()
                        .map(condition -> new StopCondition(condition.code(), condition.description()))
                        .toList(),
                request.outcomeDirection(),
                request.meaningfulChange());
        ExperimentResponse response = ExperimentResponse.from(commands.create(userId, experiment, idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/experiments/" + response.id())).body(response);
    }

    @GetMapping
    public List<ExperimentResponse> list() {
        Long userId = currentUserApi.getCurrentUserId();
        return queries.findAll(userId).stream().map(ExperimentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ExperimentResponse get(@PathVariable @Positive Long id) {
        Long userId = currentUserApi.getCurrentUserId();
        return queries.find(userId, id).map(ExperimentResponse::from)
                .orElseThrow(ExperimentNotFoundException::new);
    }

    @PostMapping("/{id}/transitions")
    public ExperimentResponse transition(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ExperimentTransitionRequest request) {
        Long userId = currentUserApi.getCurrentUserId();
        return ExperimentResponse.from(commands.transition(
                userId,
                id,
                request.command().name(),
                request.expectedVersion(),
                request.idempotencyKey(),
                request.reason()));
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
