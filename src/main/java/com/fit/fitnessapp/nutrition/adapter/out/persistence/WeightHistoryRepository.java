package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.WeightHistory;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WeightHistoryRepository implements WeightHistoryUseCase {
    
    private final WeightHistoryJpaRepository jpaRepository;
    
    @Override
    public WeightHistoryDto saveWeight(WeightHistoryDto dto) {
        WeightHistory entity = WeightHistory.builder()
                .userId(dto.userId())
                .weightKg(dto.weightKg())
                .date(dto.date())
                .source(WeightHistory.WeightSource.valueOf(dto.source().name()))
                .build();
        WeightHistory saved = jpaRepository.save(entity);
        return toDto(saved);
    }
    
    @Override
    public List<WeightHistoryDto> getWeightHistoryByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByDateDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    @Override
    public List<WeightHistoryDto> getWeightHistoryByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to) {
        return jpaRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    @Override
    public WeightHistoryDto getLatestWeightByUserId(Long userId) {
        return jpaRepository.findTopByUserIdOrderByDateDesc(userId)
                .map(this::toDto)
                .orElse(null);
    }
    
    private WeightHistoryDto toDto(WeightHistory entity) {
        return new WeightHistoryDto(
                entity.getId(),
                entity.getUserId(),
                entity.getWeightKg(),
                entity.getDate(),
                WeightHistoryDto.WeightSource.valueOf(entity.getSource().name())
        );
    }
}
