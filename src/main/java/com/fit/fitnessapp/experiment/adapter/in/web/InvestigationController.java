package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationQueryUseCase;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/investigations")
@Validated
@RequiredArgsConstructor
public class InvestigationController {
    private final InvestigationCommandUseCase commands;
    private final InvestigationQueryUseCase queries;
    private final CurrentUserApi currentUserApi;

    @PostMapping
    public ResponseEntity<InvestigationResponse> create(
            @Valid @RequestBody InvestigationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @jakarta.validation.constraints.Size(max = 128) String headerKey) {
        Long userId = currentUserApi.getCurrentUserId();
        String key = resolveIdempotencyKey(request.idempotencyKey(), headerKey);
        InvestigationResponse response = InvestigationResponse.from(
                commands.create(userId, request.title(), request.problemStatement(), key));
        return ResponseEntity.created(URI.create("/api/v1/investigations/" + response.id())).body(response);
    }

    @GetMapping
    public List<InvestigationResponse> list() {
        Long userId = currentUserApi.getCurrentUserId();
        return queries.findAll(userId).stream().map(InvestigationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public InvestigationResponse get(@PathVariable @jakarta.validation.constraints.Positive Long id) {
        Long userId = currentUserApi.getCurrentUserId();
        return queries.find(userId, id).map(InvestigationResponse::from)
                .orElseThrow(ExperimentNotFoundException::new);
    }

    @PostMapping("/{id}/transitions")
    public InvestigationResponse transition(@PathVariable @jakarta.validation.constraints.Positive Long id,
                                            @Valid @RequestBody InvestigationTransitionRequest request) {
        Long userId = currentUserApi.getCurrentUserId();
        return InvestigationResponse.from(commands.transition(userId, id, request.command().name(),
                request.expectedVersion(), request.idempotencyKey(), request.reason()));
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
