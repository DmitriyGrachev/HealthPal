package com.fit.fitnessapp.api;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared transaction fence for canonical source mutation and date-scoped projections.
 * Implementations keep the fence until the surrounding transaction completes.
 */
public interface UserDateTransactionLock {

    Optional<UUID> lockAndReadLifecycleEpoch(Long userId, LocalDate date);
}
