package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.experiment.application.port.in.GoalCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.GoalQueryUseCase;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/goals")
@Validated
@RequiredArgsConstructor
public class GoalController {
    private final GoalCommandUseCase commands;
    private final GoalQueryUseCase queries;
    private final CurrentUserApi currentUserApi;

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request,
                                               @RequestHeader(value = "Idempotency-Key", required = false)
                                               @jakarta.validation.constraints.Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        Goal goal = new Goal(null, userId, enumValue(request.type(), GoalType.class), request.name(),
                enumValue(request.metric(), GoalMetric.class),
                request.targetRange() == null ? null : request.targetRange().toDomain(),
                com.fit.fitnessapp.experiment.domain.GoalStatus.DRAFT, request.deadline(), request.priority(),
                enumValue(request.source() == null ? GoalSource.USER.name() : request.source(), GoalSource.class),
                request.investigationId(), request.supersededGoalId(), Boolean.TRUE.equals(request.primary()),
                0, Instant.now(), null, Instant.now());
        String key = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        GoalResponse response = GoalResponse.from(commands.create(userId, goal, key));
        return ResponseEntity.created(URI.create("/api/v1/goals/" + response.id())).body(response);
    }

    @GetMapping
    public List<GoalResponse> list() {
        Long userId = currentUserApi.getCurrentUserId();
        return queries.findAll(userId).stream().map(GoalResponse::from).toList();
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable @jakarta.validation.constraints.Positive Long id) {
        Long userId = currentUserApi.getCurrentUserId();
        return queries.find(userId, id).map(GoalResponse::from).orElseThrow(ExperimentNotFoundException::new);
    }

    @PostMapping("/{id}/transitions")
    public GoalResponse transition(@PathVariable @jakarta.validation.constraints.Positive Long id,
                                   @Valid @RequestBody GoalTransitionRequest request) {
        Long userId = currentUserApi.getCurrentUserId();
        return GoalResponse.from(commands.transition(userId, id, request.command().name(), request.expectedVersion(),
                request.idempotencyKey(), request.reason()));
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> type) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported goal value");
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
