package com.fit.fitnessapp.job.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.exception.ResourceNotFoundException;
import com.fit.fitnessapp.exception.DurableJobRetryConflictException;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            throw new ResourceNotFoundException("Job not found");
        }

        DurableJobDto job = jobOpt.get();
        if (currentUserId == null || !job.userId().equals(currentUserId)) {
            throw new AccessDeniedException("Access denied");
        }

        return ResponseEntity.ok(job);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getJobsByUser(@PathVariable Long userId) {
        Long currentUserId = currentUserApi.getCurrentUserId();
        if (currentUserId == null || !userId.equals(currentUserId)) {
            throw new AccessDeniedException("Access denied");
        }

        return ResponseEntity.ok(durableJobUseCase.getJobsByUser(userId));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryJob(@PathVariable Long id) {
        Long currentUserId = currentUserApi.getCurrentUserId();
        Optional<DurableJobDto> jobOpt = durableJobUseCase.getJob(id);
        if (jobOpt.isEmpty()) {
            throw new ResourceNotFoundException("Job not found");
        }

        DurableJobDto job = jobOpt.get();
        if (currentUserId == null || !job.userId().equals(currentUserId)) {
            throw new AccessDeniedException("Access denied");
        }

        if (job.status() != JobStatus.FAILED && job.status() != JobStatus.SKIPPED) {
            throw new DurableJobRetryConflictException();
        }

        if (!durableJobUseCase.retryJob(id)) {
            throw new DurableJobRetryConflictException();
        }
        return ResponseEntity.ok(new RetryResponse(id, "PENDING", "Job reset for retry"));
    }

    private record RetryResponse(Long jobId, String status, String message) {
    }
}
