package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.application.port.out.UserIdentityLifecyclePort;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import com.fit.fitnessapp.auth.domain.UserIdentityData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserDataLifecycleService implements UserDataLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDataLifecycleService.class);

    private final UserIdentityLifecyclePort userIdentityPort;
    private final List<UserDataLifecycleParticipant> participants;
    private final Clock clock;

    public UserDataLifecycleService(
            UserIdentityLifecyclePort userIdentityPort,
            List<UserDataLifecycleParticipant> participants,
            Clock clock) {
        this.userIdentityPort = userIdentityPort;
        this.participants = participants.stream()
                .sorted(Comparator.comparing(UserDataLifecycleParticipant::key))
                .toList();
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataExportDto exportUserData(Long userId) {
        log.info("Exporting personal data for userId={}", userId);
        UserIdentityData user = requireUser(userId);

        Map<String, Object> values = new HashMap<>();
        for (UserDataLifecycleParticipant participant : participants) {
            UserDataExportFragment fragment = participant.exportData(userId);
            values.putAll(fragment.values());
        }

        return new UserDataExportDto(
                userId,
                user.email(),
                user.username(),
                clock.instant(),
                mapValue(values, "profile"),
                listValue(values, "weightHistory"),
                listValue(values, "nutritionDays"),
                listValue(values, "foodEntries"),
                listValue(values, "workoutSessions"),
                listValue(values, "workoutExercises"),
                listValue(values, "workoutSets"),
                listValue(values, "workoutCardio"),
                listValue(values, "userNotes"),
                listValue(values, "aiInsights"),
                listValue(values, "memories"),
                mapValue(values, "telegramAccount"),
                mapValue(values, "conversationState"),
                listValue(values, "conversationHistory"),
                listValue(values, "telegramDeliveries"),
                listValue(values, "durableJobs"),
                Boolean.TRUE.equals(values.get("fatSecretConnected")),
                listValue(values, "aiUsageBudget"));
    }

    @Override
    @Transactional
    public void disconnectFatSecret(Long userId) {
        requireUser(userId);
        log.info("Disconnecting FatSecret OAuth connection for userId={}", userId);
        participants.forEach(participant -> participant.disconnectExternalAccount(userId));
    }

    @Override
    @Transactional
    public UserAccountDeletionResult deleteAccount(Long userId) {
        requireUser(userId);
        log.info("Initiating account deletion for userId={}", userId);

        participants.forEach(participant -> participant.deleteData(userId));
        userIdentityPort.deleteById(userId);

        log.info("Account deletion completed userId={}", userId);
        return new UserAccountDeletionResult(
                userId,
                true,
                clock.instant(),
                "Account and personal data deleted successfully.");
    }

    private UserIdentityData requireUser(Long userId) {
        return userIdentityPort.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }
}
