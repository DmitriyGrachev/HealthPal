package com.fit.fitnessapp.memory.application.port.out;

import java.time.Instant;

public interface MemoryCleanupPort {

    int deleteExpiredBefore(Instant now);
}
