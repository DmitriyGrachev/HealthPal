package com.fit.fitnessapp.nutrition.application.service;

import tools.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import org.springframework.stereotype.Component;

/** Fails closed if a legacy durable nutrition-sync job survives an upgrade race. */
@Component
public class NutritionSyncJobExecutor implements DurableJobExecutor {

    public static final String JOB_TYPE = "NUTRITION_SYNC";

    public NutritionSyncJobExecutor() {
    }

    /** Compatibility constructor for focused regression tests and old wiring. */
    public NutritionSyncJobExecutor(
            SyncNutritionUseCase ignoredSyncNutritionUseCase,
            ObjectMapper ignoredObjectMapper) {
     }

    @Override
    public boolean supports(String jobType) {
        return JOB_TYPE.equals(jobType);
    }

    @Override
    public void execute(DurableJobDto job) {
        throw new IllegalStateException("NUTRITION_SYNC_DISABLED_BY_RETENTION_POLICY");
    }
}
