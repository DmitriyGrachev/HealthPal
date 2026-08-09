package com.fit.fitnessapp.nutrition.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NutritionSyncJobExecutor implements DurableJobExecutor {

    public static final String JOB_TYPE = "NUTRITION_SYNC";

    private final SyncNutritionUseCase syncNutritionUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String jobType) {
        return JOB_TYPE.equals(jobType);
    }

    @Override
    public void execute(DurableJobDto job) throws Exception {
        NutritionSyncJobPayload payload = objectMapper.readValue(
                job.payloadJson(), NutritionSyncJobPayload.class);
        for (var date : payload.dates()) {
            syncNutritionUseCase.syncDay(job.userId(), date);
        }
    }
}
