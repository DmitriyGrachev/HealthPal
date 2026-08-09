package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.memory.application.port.out.MemoryCleanupPort;
import com.fit.fitnessapp.memory.application.service.MemoryCleanupService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCleanupServiceTest {

    @Test
    void cleanupExpiredMemoriesDeletesRowsBeforeCurrentInstant() {
        FakeMemoryCleanupPort cleanupPort = new FakeMemoryCleanupPort(3);
        Clock clock = Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC);
        MemoryCleanupService service = new MemoryCleanupService(cleanupPort, clock);

        int deleted = service.cleanupExpiredMemories();

        assertThat(deleted).isEqualTo(3);
        assertThat(cleanupPort.expiredBefore).isEqualTo(clock.instant());
    }

    private static final class FakeMemoryCleanupPort implements MemoryCleanupPort {

        private final int deletedRows;
        private Instant expiredBefore;

        private FakeMemoryCleanupPort(int deletedRows) {
            this.deletedRows = deletedRows;
        }

        @Override
        public int deleteExpiredBefore(Instant now) {
            this.expiredBefore = now;
            return deletedRows;
        }
    }
}
