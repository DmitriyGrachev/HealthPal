package com.fit.fitnessapp.api.lifecycle;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * A neutral, deterministic description of how one category of personal data is retained.
 * It describes local and processor handling only; it does not promise remote erasure.
 */
public record DataRetentionDisclosure(
        String category,
        StorageClass storageClass,
        RetentionClass retentionClass,
        Integer maximumRetentionDays,
        List<ExternalProcessor> externalProcessors,
        DeletionScope deletionScope,
        BackupLimitation backupLimitation) {

    public DataRetentionDisclosure {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        category = category.trim();
        if (storageClass == null || retentionClass == null || deletionScope == null || backupLimitation == null) {
            throw new IllegalArgumentException("disclosure classifications must not be null");
        }
        if (maximumRetentionDays != null && maximumRetentionDays <= 0) {
            throw new IllegalArgumentException("maximumRetentionDays must be positive when present");
        }
        if (retentionClass == RetentionClass.TIME_LIMITED && maximumRetentionDays == null) {
            throw new IllegalArgumentException("TIME_LIMITED disclosures require maximumRetentionDays");
        }
        if (retentionClass != RetentionClass.TIME_LIMITED && maximumRetentionDays != null) {
            throw new IllegalArgumentException(
                    "maximumRetentionDays is only valid for TIME_LIMITED disclosures");
        }
        if (retentionClass == RetentionClass.UNSPECIFIED && maximumRetentionDays != null) {
            throw new IllegalArgumentException("UNSPECIFIED disclosures cannot have a retention limit");
        }
        if (externalProcessors == null || externalProcessors.stream().anyMatch(processor -> processor == null)) {
            throw new IllegalArgumentException("externalProcessors must not contain null");
        }
        EnumSet<ExternalProcessor> uniqueProcessors = EnumSet.noneOf(ExternalProcessor.class);
        uniqueProcessors.addAll(externalProcessors);
        if (uniqueProcessors.size() != externalProcessors.size()) {
            throw new IllegalArgumentException("externalProcessors must not contain duplicates");
        }
        externalProcessors = List.copyOf(uniqueProcessors).stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    public static DataRetentionDisclosure unspecified(String category) {
        return new DataRetentionDisclosure(
                category,
                StorageClass.UNSPECIFIED,
                RetentionClass.UNSPECIFIED,
                null,
                List.of(),
                DeletionScope.UNSPECIFIED,
                BackupLimitation.SUBJECT_TO_BACKUP_RETENTION);
    }

    public enum StorageClass {
        LOCAL_CANONICAL,
        LOCAL_DERIVED,
        LOCAL_OPERATIONAL,
        LOCAL_PROVIDER_COPY,
        EXPIRING_PROVIDER_CACHE,
        UNSPECIFIED
    }

    public enum RetentionClass {
        ACCOUNT_LIFETIME,
        HORIZON_BOUND,
        TIME_LIMITED,
        UNTIL_TERMINAL,
        UNSPECIFIED
    }

    public enum ExternalProcessor {
        FATSECRET,
        AI_PROVIDER,
        TELEGRAM
    }

    public enum DeletionScope {
        LOCAL_PRIMARY_AND_DERIVED,
        LOCAL_CACHE,
        LOCAL_OPERATIONAL,
        UNSPECIFIED
    }

    public enum BackupLimitation {
        SUBJECT_TO_BACKUP_RETENTION
    }
}
