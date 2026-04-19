package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.Profile;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.domain.ProfileSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProfileJpaRepository implements ProfileUseCase {
    
    private final ProfileJpaRepositoryImpl jpaRepository;
    
    @Override
    public Optional<ProfileSummaryDto> getProfileByUserId(Long userId) {
        return jpaRepository.findByUserId(userId)
                .map(this::toDto);
    }
    
    @Override
    public ProfileSummaryDto saveProfile(ProfileSummaryDto dto) {
        Profile entity = jpaRepository.findByUserId(dto.userId())
                .orElseGet(() -> {
                    Profile newEntity = new Profile();
                    newEntity.setUserId(dto.userId());
                    return newEntity;
                });
        
        entity.setAge(dto.age());
        entity.setGender(dto.gender() != null ? Profile.Gender.valueOf(dto.gender().name()) : null);
        entity.setPrimaryGoal(dto.primaryGoal() != null ? Profile.FitnessGoal.valueOf(dto.primaryGoal().name()) : null);
        entity.setTargetWeightKg(dto.targetWeightKg());
        entity.setTargetDate(dto.targetDate());
        entity.setLastWeightKg(dto.lastWeightKg());
        
        Profile saved = jpaRepository.save(entity);
        return toDto(saved);
    }
    
    private ProfileSummaryDto toDto(Profile entity) {
        return new ProfileSummaryDto(
                entity.getUserId(),
                entity.getAge(),
                entity.getGender() != null ? ProfileSummaryDto.Gender.valueOf(entity.getGender().name()) : null,
                entity.getPrimaryGoal() != null ? ProfileSummaryDto.FitnessGoal.valueOf(entity.getPrimaryGoal().name()) : null,
                entity.getTargetWeightKg(),
                entity.getTargetDate(),
                entity.getLastWeightKg()
        );
    }
}
