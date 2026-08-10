package com.fit.fitnessapp.api;

import java.util.Map;

public record UserDataExportFragment(String participantKey, Map<String, Object> values) {

    public UserDataExportFragment {
        values = Map.copyOf(values);
    }
}
