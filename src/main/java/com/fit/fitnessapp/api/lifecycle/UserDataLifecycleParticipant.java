package com.fit.fitnessapp.api.lifecycle;

import java.util.List;

public interface UserDataLifecycleParticipant {

    String key();

    UserDataExportFragment exportData(Long userId);

    void deleteData(Long userId);

    default int exportSchemaVersion() {
        return 1;
    }

    default List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(DataRetentionDisclosure.unspecified(key()));
    }

    default void disconnectExternalAccount(Long userId) {
    }
}
