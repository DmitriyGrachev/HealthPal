package com.fit.fitnessapp.auth.application.port.in;

import com.fit.fitnessapp.auth.domain.UserDataExportManifest;

public interface UserDataExportManifestUseCase {

    UserDataExportManifest exportUserData(Long userId);
}
