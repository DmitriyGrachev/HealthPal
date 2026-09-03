package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.domain.ClaimConflict;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/conflicts")
@RequiredArgsConstructor
public class ClaimConflictController {
    private final ClaimConflictQueryUseCase queries;
    private final CurrentUserApi currentUser;

    @GetMapping
    public List<ClaimConflict> list() {
        return queries.findOpen(currentUser.getCurrentUserId());
    }
}
