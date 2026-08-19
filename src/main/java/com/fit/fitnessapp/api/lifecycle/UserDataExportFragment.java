package com.fit.fitnessapp.api.lifecycle;

import java.util.Map;

public record UserDataExportFragment(String participantKey, Map<String, Object> values) {

    public UserDataExportFragment {
        if (participantKey == null || participantKey.isBlank()) {
            throw new IllegalArgumentException("participantKey must not be blank");
        }
        values = Map.copyOf(values);
    }
}
