package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictCommandUseCase;
import com.fit.fitnessapp.knowledge.domain.ClaimDrift;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.fit.fitnessapp.knowledge.domain.ClaimConflict;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/conflicts")
@RequiredArgsConstructor
public class ClaimConflictController {
    private final ClaimConflictQueryUseCase queries;
    private final CurrentUserApi currentUser;
    private final ClaimConflictCommandUseCase commands;

    @GetMapping
    public List<ClaimConflict> list() {
        return queries.findOpen(currentUser.getCurrentUserId());
    }

    @GetMapping("/drift")
    public List<ClaimDrift> drift() { return commands.drift(currentUser.getCurrentUserId()); }

    @PostMapping("/{id}/acknowledge")
    public ClaimConflict acknowledge(@PathVariable @Positive Long id, @RequestBody @Valid ClaimCommandRequest request) {
        return commands.acknowledge(currentUser.getCurrentUserId(), id, request.expectedVersion(), request.idempotencyKey());
    }

    @PostMapping("/{id}/dismiss")
    public ClaimConflict dismiss(@PathVariable @Positive Long id, @RequestBody @Valid ClaimCommandRequest request) {
        return commands.dismiss(currentUser.getCurrentUserId(), id, request.expectedVersion(), request.idempotencyKey());
    }
}
