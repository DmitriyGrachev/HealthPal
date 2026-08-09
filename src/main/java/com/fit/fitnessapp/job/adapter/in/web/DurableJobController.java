package com.fit.fitnessapp.job.adapter.in.web;

import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class DurableJobController {

    private final DurableJobUseCase durableJobUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<DurableJobDto> getJob(@PathVariable Long id) {
        return durableJobUseCase.getJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<DurableJobDto>> getJobsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(durableJobUseCase.getJobsByUser(userId));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryJob(@PathVariable Long id) {
        durableJobUseCase.retryJob(id);
        return ResponseEntity.ok(Map.of(
                "jobId", id,
                "status", "PENDING",
                "message", "Job reset for operator retry"
        ));
    }
}
