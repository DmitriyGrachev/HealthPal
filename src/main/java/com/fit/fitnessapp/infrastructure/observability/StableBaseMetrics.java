package com.fit.fitnessapp.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Safe operational gauges; values contain no user content or provider payloads. */
@Component
@ConditionalOnBean(MeterRegistry.class)
public class StableBaseMetrics {

    public StableBaseMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        register(registry, jdbcTemplate, "fitnessapp.durable_jobs.pending",
                "SELECT COUNT(*) FROM durable_jobs WHERE status = 'PENDING'");
        register(registry, jdbcTemplate, "fitnessapp.durable_jobs.failed",
                "SELECT COUNT(*) FROM durable_jobs WHERE status = 'FAILED'");
        register(registry, jdbcTemplate, "fitnessapp.telegram_outbox.pending",
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE status IN ('PENDING', 'SENDING')");
        register(registry, jdbcTemplate, "fitnessapp.telegram_outbox.failed",
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE status = 'FAILED'");
        register(registry, jdbcTemplate, "fitnessapp.event_publication.incomplete",
                "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL");
    }

    private void register(MeterRegistry registry, JdbcTemplate jdbcTemplate, String name, String sql) {
        registry.gauge(name, jdbcTemplate, template -> {
            try {
                Number value = template.queryForObject(sql, Number.class);
                return value == null ? 0.0 : value.doubleValue();
            } catch (RuntimeException ignored) {
                return -1.0;
            }
        });
    }
}
