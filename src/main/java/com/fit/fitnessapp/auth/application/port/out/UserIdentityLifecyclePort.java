package com.fit.fitnessapp.auth.application.port.out;

import com.fit.fitnessapp.auth.domain.UserIdentityData;

import java.util.Optional;

public interface UserIdentityLifecyclePort {

    Optional<UserIdentityData> findById(Long userId);

    Optional<UserIdentityData> findByIdForUpdate(Long userId);

    void deleteById(Long userId);
}
