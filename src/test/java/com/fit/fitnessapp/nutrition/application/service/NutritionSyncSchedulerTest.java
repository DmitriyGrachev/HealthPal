package com.fit.fitnessapp.nutrition.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NutritionSyncSchedulerTest {

    @Test
    void historicalProviderSyncHasNoSchedulingTriggerAndDoesNoWork() throws Exception {
        NutritionSyncScheduler scheduler = new NutritionSyncScheduler();

        assertThat(NutritionSyncScheduler.class.getDeclaredMethod("syncAllUsersToday")
                .getAnnotation(Scheduled.class)).isNull();
        assertThatCode(scheduler::syncAllUsersToday).doesNotThrowAnyException();
        assertThatCode(scheduler::syncAllUsersRecentWindow).doesNotThrowAnyException();
    }
}
