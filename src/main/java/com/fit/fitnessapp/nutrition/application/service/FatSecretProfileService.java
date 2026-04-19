package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.WeightHistoryRepository;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

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
            log.error("Failed to get user summary from FatSecret for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to get user summary", e);
        }
    }
    
    @Transactional
    public void syncProfileFromFatSecret(Long userId, FatSecretAuthResult authResult) {
        try {
            WeightEntryDto latestWeight = fatSecretApi.getLatestWeight(authResult.token());
            if (latestWeight != null) {
                List<WeightHistoryDto> existing = weightHistoryRepository.getWeightHistoryByUserIdAndDateRange(
                        userId, latestWeight.date(), latestWeight.date());
                
                boolean existsToday = existing.stream()
                        .anyMatch(e -> e.source() == WeightHistoryDto.WeightSource.FATSECRET);
                
                if (!existsToday) {
                    WeightHistoryDto dto = new WeightHistoryDto(
                            null, userId,
                            latestWeight.weight().stripTrailingZeros(),
                            latestWeight.date(),
                            WeightHistoryDto.WeightSource.FATSECRET
                    );
                    weightHistoryRepository.saveWeight(dto);
                    log.info("Synced weight from FatSecret for user {}: {}kg on {}", 
                            userId, latestWeight.weight(), latestWeight.date());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to sync profile from FatSecret for user {}: {}", userId, e.getMessage());
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

                    boolean exists = existing.stream()
                            .anyMatch(e -> e.source() == WeightHistoryDto.WeightSource.FATSECRET);

                    if (!exists) {
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
                log.info("Synced {} new weight entries from FatSecret for user {} for date {}",
                        savedCount, userId, date);
            }
        } catch (Exception e) {
            log.warn("Failed to sync weight history from FatSecret for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public boolean updateWeightOnFatSecret(FatSecretAuthResult authResult, WeightEntryDto weightEntry) {
        try {
            boolean success = fatSecretApi.updateWeight(authResult.token(), weightEntry);
            if (success) {
                log.info("Successfully updated weight on FatSecret for user {}: {}kg", 
                        authResult.userId(), weightEntry.weight());
                
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
            log.error("Failed to update weight on FatSecret for user {}: {}", authResult.userId(), e.getMessage());
            return false;
        }
    }
}
