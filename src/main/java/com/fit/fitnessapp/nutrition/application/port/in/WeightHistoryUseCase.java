package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import java.time.LocalDate;
import java.util.List;

public interface WeightHistoryUseCase {
    WeightHistoryDto saveWeight(WeightHistoryDto weight);
    List<WeightHistoryDto> getWeightHistoryByUserId(Long userId);
    List<WeightHistoryDto> getWeightHistoryByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);
    WeightHistoryDto getLatestWeightByUserId(Long userId);
}
