package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.nutrition.domain.ProfileSummaryDto;
import java.util.Optional;

public interface ProfileUseCase {
    Optional<ProfileSummaryDto> getProfileByUserId(Long userId);
    ProfileSummaryDto saveProfile(ProfileSummaryDto profile);
}
