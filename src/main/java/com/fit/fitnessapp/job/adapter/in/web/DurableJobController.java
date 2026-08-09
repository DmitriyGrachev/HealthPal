package com.fit.fitnessapp.job.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class DurableJobController {

    private final DurableJobUseCase durableJobUseCase;
    private final CurrentUserApi currentUserApi;

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable Long id) {
        Long currentUserId = currentUserApi.getCurrentUserId();
        Optional<DurableJobDto> jobOpt = durableJobUseCase.getJob(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DurableJobDto job = jobOpt.get();
        if (currentUserId == null || !job.userId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        return ResponseEntity.ok(job);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getJobsByUser(@PathVariable Long userId) {
        Long currentUserId = currentUserApi.getCurrentUserId();
        if (currentUserId == null || !userId.equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        return ResponseEntity.ok(durableJobUseCase.getJobsByUser(userId));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryJob(@PathVariable Long id) {
        Long currentUserId = currentUserApi.getCurrentUserId();
        Optional<DurableJobDto> jobOpt = durableJobUseCase.getJob(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DurableJobDto job = jobOpt.get();
        if (currentUserId == null || !job.userId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        durableJobUseCase.retryJob(id);
        return ResponseEntity.ok(Map.of(
                "jobId", id,
                "status", "PENDING",
                "message", "Job reset for retry"
        ));
    }
}
