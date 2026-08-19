package com.fit.fitnessapp.auth.domain;

import java.time.Instant;
import java.util.List;

public record UserDataExportManifest(
        int manifestVersion,
        Long userId,
        String email,
        String username,
        Instant exportedAt,
        List<UserDataModuleExport> modules) {

    public UserDataExportManifest {
        if (manifestVersion != 2) {
            throw new IllegalArgumentException("manifestVersion must be exactly 2");
        }
        if (userId == null || email == null || username == null || exportedAt == null) {
            throw new IllegalArgumentException("manifest identity and timestamp are required");
        }
        if (modules == null || modules.stream().anyMatch(module -> module == null)) {
            throw new IllegalArgumentException("modules must not contain null");
        }
        modules = List.copyOf(modules);
    }
}
