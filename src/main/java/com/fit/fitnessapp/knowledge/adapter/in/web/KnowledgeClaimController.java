package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimQueryUseCase;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimNotFoundException;
import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/claims")
@Validated
@RequiredArgsConstructor
public class KnowledgeClaimController {
    private final KnowledgeClaimInspectorUseCase commands;
    private final KnowledgeClaimQueryUseCase queries;
    private final CurrentUserApi currentUser;

    @GetMapping
    public List<KnowledgeClaimResponse> list() {
        return queries.findAll(currentUser.getCurrentUserId()).stream().map(KnowledgeClaimResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ClaimDetailResponse get(@PathVariable @Positive Long id) {
        Long owner = currentUser.getCurrentUserId();
        KnowledgeClaimResponse claim = queries.find(owner, id).map(KnowledgeClaimResponse::from)
                .orElseThrow(KnowledgeClaimNotFoundException::new);
        return new ClaimDetailResponse(claim,
                queries.findHistory(owner, id).stream().map(KnowledgeClaimResponse::from).toList());
    }

    @PostMapping("/{id}/confirm")
    public KnowledgeClaimResponse confirm(@PathVariable @Positive Long id,
                                          @Valid @RequestBody ClaimCommandRequest request) {
        return KnowledgeClaimResponse.from(commands.confirm(currentUser.getCurrentUserId(), id,
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/{id}/dispute")
    public KnowledgeClaimResponse dispute(@PathVariable @Positive Long id,
                                          @Valid @RequestBody ClaimCommandRequest request) {
        return KnowledgeClaimResponse.from(commands.dispute(currentUser.getCurrentUserId(), id,
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PutMapping("/{id}")
    public KnowledgeClaimResponse correct(@PathVariable @Positive Long id,
                                          @Valid @RequestBody ClaimCorrectionRequest request) {
        return KnowledgeClaimResponse.from(commands.correctByUser(currentUser.getCurrentUserId(), id,
                new ClaimSubject(request.subject()), new ClaimPredicate(request.predicate()), request.value().toDomain(),
                request.observedAt(), request.validFrom(), request.validUntil(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> forget(@PathVariable @Positive Long id,
                                      @RequestParam @Min(0) long expectedVersion,
                                      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        commands.forget(currentUser.getCurrentUserId(), id, expectedVersion, key);
        return ResponseEntity.noContent().build();
    }

    public record ClaimDetailResponse(KnowledgeClaimResponse claim, List<KnowledgeClaimResponse> history) { }
}
