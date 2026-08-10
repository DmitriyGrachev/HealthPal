package com.fit.fitnessapp.api;

public interface UserDataLifecycleParticipant {

    String key();

    UserDataExportFragment exportData(Long userId);

    void deleteData(Long userId);

    default void disconnectExternalAccount(Long userId) {
    }
}
