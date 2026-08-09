package com.fit.fitnessapp.infrastructure.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StableBaseMetricsTest {

    @Test
    void registersOnlySafeOperationalGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Number.class))).thenReturn(3L);

        new StableBaseMetrics(registry, jdbc);

        assertThat(registry.get("fitnessapp.durable_jobs.pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("fitnessapp.event_publication.incomplete").gauge().value()).isEqualTo(3.0);
        assertThat(registry.getMeters()).hasSize(5);
    }
}
