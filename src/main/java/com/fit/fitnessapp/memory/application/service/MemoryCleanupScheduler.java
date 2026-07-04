package com.fit.fitnessapp.memory.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "memory.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryCleanupScheduler {

    private final MemoryCleanupService cleanupService;

    @Scheduled(cron = "${memory.cleanup.cron}")
    public void cleanupExpiredMemories() {
        int deletedRows = cleanupService.cleanupExpiredMemories();
        log.info("Expired user memory cleanup completed deletedRows={}", deletedRows);
    }
}
