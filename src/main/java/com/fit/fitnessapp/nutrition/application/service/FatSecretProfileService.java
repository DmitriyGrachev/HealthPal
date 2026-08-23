package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.exception.ExternalApiException;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fit.fitnessapp.nutrition.domain.FatSecretExerciseEntryDto;
import com.fit.fitnessapp.nutrition.domain.FatSecretUserSummaryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.util.List;

@Service
public class FatSecretProfileService {
    
    private static final Logger log = LoggerFactory.getLogger(FatSecretProfileService.class);

    private final FatSecretApiPort fatSecretApi;
    private final WeightHistoryUseCase weightHistoryRepository;
    private final UserTimeApi userTimeApi;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FatSecretProfileService(FatSecretApiPort fatSecretApi, WeightHistoryUseCase weightHistoryRepository,
                                   UserTimeApi userTimeApi, Clock clock) {
        this.fatSecretApi = fatSecretApi;
        this.weightHistoryRepository = weightHistoryRepository;
        this.userTimeApi = userTimeApi;
        this.clock = clock;
    }

    public FatSecretProfileService(FatSecretApiPort fatSecretApi, WeightHistoryUseCase weightHistoryRepository) {
        this(fatSecretApi, weightHistoryRepository, null, Clock.systemUTC());
    }
    
    public FatSecretUserSummaryDto getUserSummary(Long userId, FatSecretAuthResult authResult) {
        try {
            WeightEntryDto latestWeight = fatSecretApi.getLatestWeight(authResult.token());
            List<FatSecretExerciseEntryDto> exercises = fatSecretApi.getExerciseEntries(
                    authResult.token(), currentDate(userId).toEpochDay());
            
            BigDecimal currentWeight = latestWeight != null ? latestWeight.weight() : BigDecimal.ZERO;
            
            int totalMinutes = exercises.stream()
                    .mapToInt(FatSecretExerciseEntryDto::minutes)
                    .sum();
            
            BigDecimal totalCalories = exercises.stream()
                    .map(FatSecretExerciseEntryDto::calories)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new FatSecretUserSummaryDto(
                    userId,
                    currentWeight,
                    totalMinutes,
                    totalCalories,
                    exercises
            );
        } catch (Exception e) {
            log.error("FatSecret user summary failed userId={} errorCode={}", userId, e.getClass().getSimpleName());
            throw new ExternalApiException("Failed to get user summary from FatSecret", e);
        }
    }
    
    public void syncProfileFromFatSecret(Long userId, FatSecretAuthResult authResult) {
        log.debug(
                "FatSecret profile persistence skipped userId={} reasonCode=PROVIDER_CONTENT_NON_RETENTION",
                userId);
    }
    
    public void syncWeightHistoryFromFatSecret(Long userId, FatSecretAuthResult authResult, LocalDate date) {
        log.debug(
                "FatSecret weight history persistence skipped userId={} date={} "
                        + "reasonCode=PROVIDER_CONTENT_NON_RETENTION",
                userId,
                date);
    }

    public boolean updateWeightOnFatSecret(FatSecretAuthResult authResult, WeightEntryDto weightEntry) {
        try {
            boolean success = fatSecretApi.updateWeight(authResult.token(), weightEntry);
            if (success) {
                log.info("FatSecret weight update completed userId={} date={} status=success",
                        authResult.userId(),
                        weightEntry.date() != null ? weightEntry.date() : currentDate(authResult.userId()));
                
            }
            return success;
        } catch (Exception e) {
            log.error("FatSecret weight update failed userId={} status=error errorCode={}",
                    authResult.userId(), e.getClass().getSimpleName());
            return false;
        }
    }

    private LocalDate currentDate(Long userId) {
        return userTimeApi != null ? userTimeApi.currentDate(userId) : LocalDate.now(clock);
    }
}
