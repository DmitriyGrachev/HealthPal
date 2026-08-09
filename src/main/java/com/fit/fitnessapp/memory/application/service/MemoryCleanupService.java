package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.memory.application.port.out.MemoryCleanupPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class MemoryCleanupService {

    private final MemoryCleanupPort cleanupPort;
    private final Clock clock;

    @Autowired
    public MemoryCleanupService(MemoryCleanupPort cleanupPort) {
        this(cleanupPort, Clock.systemUTC());
    }

    public MemoryCleanupService(MemoryCleanupPort cleanupPort, Clock clock) {
        this.cleanupPort = cleanupPort;
        this.clock = clock;
    }

    public int cleanupExpiredMemories() {
        return cleanupExpiredMemories(clock.instant());
    }

    public int cleanupExpiredMemories(Instant now) {
        return cleanupPort.deleteExpiredBefore(now);
    }
}
