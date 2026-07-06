package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.exception.ExternalApiException;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fit.fitnessapp.nutrition.domain.FatSecretExerciseEntryDto;
import com.fit.fitnessapp.nutrition.domain.FatSecretUserSummaryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FatSecretProfileService {
    
    private final FatSecretApiPort fatSecretApi;
    private final WeightHistoryUseCase weightHistoryRepository;
    
    public FatSecretUserSummaryDto getUserSummary(Long userId, FatSecretAuthResult authResult) {
        try {
            WeightEntryDto latestWeight = fatSecretApi.getLatestWeight(authResult.token());
            List<FatSecretExerciseEntryDto> exercises = fatSecretApi.getExerciseEntries(
                    authResult.token(), LocalDate.now().toEpochDay());
            
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
    
    @Transactional
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
        }
    }
    
    @Transactional
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
        }
    }

    @Transactional
    public boolean updateWeightOnFatSecret(FatSecretAuthResult authResult, WeightEntryDto weightEntry) {
        try {
            boolean success = fatSecretApi.updateWeight(authResult.token(), weightEntry);
            if (success) {
                log.info("FatSecret weight update completed userId={} date={} status=success",
                        authResult.userId(),
                        weightEntry.date() != null ? weightEntry.date() : LocalDate.now());
                
                // Also save to local history
                WeightHistoryDto dto = new WeightHistoryDto(
                        null, authResult.userId(),
                        weightEntry.weight().stripTrailingZeros(),
                        weightEntry.date() != null ? weightEntry.date() : LocalDate.now(),
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
}
