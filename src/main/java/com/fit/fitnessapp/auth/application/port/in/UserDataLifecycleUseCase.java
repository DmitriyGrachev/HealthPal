package com.fit.fitnessapp.auth.application.port.in;

import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;

public interface UserDataLifecycleUseCase {
    UserDataExportDto exportUserData(Long userId);
    void disconnectFatSecret(Long userId);
    UserAccountDeletionResult deleteAccount(Long userId);
}
