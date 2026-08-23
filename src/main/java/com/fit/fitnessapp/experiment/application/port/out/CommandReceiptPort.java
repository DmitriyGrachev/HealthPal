package com.fit.fitnessapp.experiment.application.port.out;

import java.time.Instant;
import java.util.Optional;

public interface CommandReceiptPort {
    Optional<CommandReceipt> find(Long userId, String aggregateType, String idempotencyKey);
    boolean insert(Long userId, String aggregateType, Long aggregateId, String idempotencyKey, long resultVersion,
                   String requestFingerprint, Instant createdAt);

    record CommandReceipt(Long userId, String aggregateType, Long aggregateId,
                          String idempotencyKey, long resultVersion, String requestFingerprint,
                          Instant createdAt) { }
}
