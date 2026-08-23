package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.FatSecretConnectionSnapshot;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NutritionCommandPort {
    void saveToken(Long userId, FatSecretToken token);
    Optional<FatSecretToken> getToken(Long userId);
    Optional<FatSecretConnectionSnapshot> getConnectionSnapshot(Long userId);
    List<Long> getAllConnectedUserIds();
    int saveProviderIdentifiers(
            FatSecretConnectionSnapshot connection,
            Set<ProviderDataIdentifier> identifiers);
    List<ProviderDataIdentifier> getProviderIdentifiers(Long userId);
}
