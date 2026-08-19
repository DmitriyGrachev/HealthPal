package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import com.fit.fitnessapp.auth.application.port.in.UserDataExportManifestUseCase;
import com.fit.fitnessapp.auth.application.port.out.UserIdentityLifecyclePort;
import com.fit.fitnessapp.auth.domain.UserDataExportManifest;
import com.fit.fitnessapp.auth.domain.UserDataModuleExport;
import com.fit.fitnessapp.auth.domain.UserIdentityData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
public class UserDataExportManifestService implements UserDataExportManifestUseCase {

    private final UserIdentityLifecyclePort userIdentityPort;
    private final List<UserDataLifecycleParticipant> participants;
    private final Clock clock;

    public UserDataExportManifestService(
            UserIdentityLifecyclePort userIdentityPort,
            List<UserDataLifecycleParticipant> participants,
            Clock clock) {
        this.userIdentityPort = userIdentityPort;
        if (participants == null || participants.stream().anyMatch(participant -> participant == null)) {
            throw new IllegalArgumentException("participants must not contain null");
        }
        this.participants = List.copyOf(participants);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataExportManifest exportUserData(Long userId) {
        UserIdentityData user = userIdentityPort.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<UserDataModuleExport> modules = validatedParticipants().stream()
                .map(participant -> exportModule(participant, userId))
                .toList();

        return new UserDataExportManifest(
                2,
                userId,
                user.email(),
                user.username(),
                clock.instant(),
                modules);
    }

    private UserDataModuleExport exportModule(ParticipantEntry participant, Long userId) {
        UserDataExportFragment fragment = participant.participant().exportData(userId);
        if (!participant.key().equals(normalizeKey(fragment.participantKey()))) {
            throw new IllegalStateException(
                    "Participant key mismatch for " + participant.key() + ": " + fragment.participantKey());
        }
        return new UserDataModuleExport(
                participant.key(),
                participant.participant().exportSchemaVersion(),
                participant.participant().retentionDisclosure(),
                fragment.values());
    }

    private List<ParticipantEntry> validatedParticipants() {
        Set<String> keys = new HashSet<>();
        List<ParticipantEntry> entries = new ArrayList<>();
        for (UserDataLifecycleParticipant participant : participants) {
            String key = normalizeKey(participant.key());
            if (!keys.add(key)) {
                throw new IllegalArgumentException("participant keys must be nonblank and unique");
            }
            entries.add(new ParticipantEntry(participant, key));
        }
        return entries.stream()
                .sorted(Comparator.comparing(ParticipantEntry::key))
                .toList();
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("participant keys must be nonblank and unique");
        }
        return key.trim();
    }

    private record ParticipantEntry(UserDataLifecycleParticipant participant, String key) {
    }
}
