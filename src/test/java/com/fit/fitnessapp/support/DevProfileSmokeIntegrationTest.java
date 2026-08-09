package com.fit.fitnessapp.support;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("dev")
class DevProfileSmokeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Test
    void devProfileStartsAgainstTheCleanPostgresBaseline() {
        assertThat(POSTGRES.isRunning()).isTrue();
    }
}
