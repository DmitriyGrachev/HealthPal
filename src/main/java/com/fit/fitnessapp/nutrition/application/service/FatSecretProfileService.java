package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.exception.ExternalApiException;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
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
        try {
            WeightEntryDto latestWeight = fatSecretApi.getLatestWeight(authResult.token());
            if (latestWeight != null) {
                List<WeightHistoryDto> existing = weightHistoryRepository.getWeightHistoryByUserIdAndDateRange(
                        userId, latestWeight.date(), latestWeight.date());
                
                if (shouldSaveFatSecretWeight(existing, latestWeight.weight())) {
                    WeightHistoryDto dto = new WeightHistoryDto(
                            null, userId,
                            latestWeight.weight().stripTrailingZeros(),
                            latestWeight.date(),
                            WeightHistoryDto.WeightSource.FATSECRET
                    );
                    weightHistoryRepository.saveWeight(dto);
                }
                log.info("FatSecret profile sync completed userId={} date={} status=success",
                        userId, latestWeight.date());
            }
        } catch (Exception e) {
            log.warn("FatSecret profile sync failed userId={} status=error errorCode={}",
                    userId, e.getClass().getSimpleName());
            throw new ExternalApiException("Failed to sync FatSecret profile", e);
        }
    }
    
    public void syncWeightHistoryFromFatSecret(Long userId, FatSecretAuthResult authResult, LocalDate date) {
        try {
            List<WeightEntryDto> weightHistory = fatSecretApi.getWeightHistory(authResult.token(), date.toEpochDay());
            if (weightHistory != null && !weightHistory.isEmpty()) {
                int savedCount = 0;
                for (WeightEntryDto entry : weightHistory) {
                    List<WeightHistoryDto> existing = weightHistoryRepository.getWeightHistoryByUserIdAndDateRange(
                            userId, entry.date(), entry.date());

                    if (shouldSaveFatSecretWeight(existing, entry.weight())) {
                        WeightHistoryDto dto = new WeightHistoryDto(
                                null, userId,
                                entry.weight().stripTrailingZeros(),
                                entry.date(),
                                WeightHistoryDto.WeightSource.FATSECRET
                        );
                        weightHistoryRepository.saveWeight(dto);
                        savedCount++;
                    }
                }
                log.info("Synced {} weight entries from FatSecret for user {} for date {}",
                        savedCount, userId, date);
            }
        } catch (Exception e) {
            log.warn("FatSecret weight history sync failed userId={} status=error errorCode={}",
                    userId, e.getClass().getSimpleName());
            throw new ExternalApiException("Failed to sync FatSecret weight history", e);
        }
    }

    public boolean updateWeightOnFatSecret(FatSecretAuthResult authResult, WeightEntryDto weightEntry) {
        try {
            boolean success = fatSecretApi.updateWeight(authResult.token(), weightEntry);
            if (success) {
                log.info("FatSecret weight update completed userId={} date={} status=success",
                        authResult.userId(),
                        weightEntry.date() != null ? weightEntry.date() : currentDate(authResult.userId()));
                
                // Also save to local history
                WeightHistoryDto dto = new WeightHistoryDto(
                        null, authResult.userId(),
                        weightEntry.weight().stripTrailingZeros(),
                        weightEntry.date() != null ? weightEntry.date() : currentDate(authResult.userId()),
                        WeightHistoryDto.WeightSource.FATSECRET
                );
                weightHistoryRepository.saveWeight(dto);
            }
            return success;
        } catch (Exception e) {
            log.error("FatSecret weight update failed userId={} status=error errorCode={}",
                    authResult.userId(), e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean shouldSaveFatSecretWeight(List<WeightHistoryDto> existing, BigDecimal incomingWeight) {
        return existing.stream()
                .filter(entry -> entry.source() == WeightHistoryDto.WeightSource.FATSECRET)
                .noneMatch(entry -> entry.weightKg().compareTo(incomingWeight) == 0);
    }

    private LocalDate currentDate(Long userId) {
        return userTimeApi != null ? userTimeApi.currentDate(userId) : LocalDate.now(clock);
    }
}
