package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import com.fit.fitnessapp.auth.application.port.out.UserIdentityLifecyclePort;
import com.fit.fitnessapp.auth.domain.UserDataExportManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataExportManifestServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private UserIdentityLifecyclePort identityPort;

    @Mock
    private UserDataLifecycleParticipant workout;

    @Mock
    private UserDataLifecycleParticipant auth;

    @Test
    void exportsCurrentIdentityWithSortedModuleOwnedVersionedDataAndDisclosures() {
        when(identityPort.findById(42L)).thenReturn(Optional.of(
                new com.fit.fitnessapp.auth.domain.UserIdentityData(42L, "me@example.test", "me")));
        when(workout.key()).thenReturn("workout");
        when(workout.exportSchemaVersion()).thenReturn(3);
        when(workout.retentionDisclosure()).thenReturn(List.of(disclosure("workout")));
        when(workout.exportData(42L)).thenReturn(new UserDataExportFragment(
                "workout", Map.of("sessions", List.of(Map.of("id", 7L)))));
        when(auth.key()).thenReturn("auth");
        when(auth.exportSchemaVersion()).thenReturn(1);
        when(auth.retentionDisclosure()).thenReturn(List.of(disclosure("identity")));
        when(auth.exportData(42L)).thenReturn(new UserDataExportFragment(
                "auth", Map.of("notes", List.of(Map.of("id", 9L)))));

        UserDataExportManifest manifest = new UserDataExportManifestService(
                identityPort, List.of(workout, auth), CLOCK).exportUserData(42L);

        assertThat(manifest.manifestVersion()).isEqualTo(2);
        assertThat(manifest.userId()).isEqualTo(42L);
        assertThat(manifest.email()).isEqualTo("me@example.test");
        assertThat(manifest.username()).isEqualTo("me");
        assertThat(manifest.exportedAt()).isEqualTo(Instant.parse("2026-08-19T10:15:30Z"));
        assertThat(manifest.modules()).extracting(module -> module.moduleKey())
                .containsExactly("auth", "workout");
        assertThat(manifest.modules().getFirst().schemaVersion()).isEqualTo(1);
        assertThat(manifest.modules().getFirst().data()).containsEntry(
                "notes", List.of(Map.of("id", 9L)));
        assertThat(manifest.modules().get(1).schemaVersion()).isEqualTo(3);
        assertThat(manifest.modules().get(1).retentionDisclosure())
                .extracting(DataRetentionDisclosure::category)
                .containsExactly("workout");

        verify(auth).exportData(42L);
        verify(workout).exportData(42L);
    }

    @Test
    void unknownUserIsRejectedBeforeAnyParticipantRuns() {
        when(identityPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UserDataExportManifestService(
                identityPort, List.of(workout), CLOCK).exportUserData(999L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(workout, never()).exportData(999L);
    }

    @Test
    void participantKeyMismatchIsRejected() {
        when(identityPort.findById(42L)).thenReturn(Optional.of(
                new com.fit.fitnessapp.auth.domain.UserIdentityData(42L, "me@example.test", "me")));
        when(workout.key()).thenReturn("workout");
        when(workout.exportData(42L)).thenReturn(new UserDataExportFragment(
                "different", Map.of()));

        assertThatThrownBy(() -> new UserDataExportManifestService(
                identityPort, List.of(workout), CLOCK).exportUserData(42L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalizedDuplicateParticipantKeysAreRejectedBeforeExport() {
        when(identityPort.findById(42L)).thenReturn(Optional.of(
                new com.fit.fitnessapp.auth.domain.UserIdentityData(42L, "me@example.test", "me")));
        when(workout.key()).thenReturn(" auth ");
        when(auth.key()).thenReturn("auth");

        assertThatThrownBy(() -> new UserDataExportManifestService(
                identityPort, List.of(workout, auth), CLOCK).exportUserData(42L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(workout, never()).exportData(42L);
        verify(auth, never()).exportData(42L);
    }

    @Test
    void exactDuplicateParticipantKeysAreRejectedBeforeExport() {
        when(identityPort.findById(42L)).thenReturn(Optional.of(
                new com.fit.fitnessapp.auth.domain.UserIdentityData(42L, "me@example.test", "me")));
        when(workout.key()).thenReturn("auth");
        when(auth.key()).thenReturn("auth");

        assertThatThrownBy(() -> new UserDataExportManifestService(
                identityPort, List.of(workout, auth), CLOCK).exportUserData(42L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(workout, never()).exportData(42L);
        verify(auth, never()).exportData(42L);
    }

    private static DataRetentionDisclosure disclosure(String category) {
        return new DataRetentionDisclosure(
                category,
                DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                null,
                List.of(DataRetentionDisclosure.ExternalProcessor.AI_PROVIDER),
                DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION);
    }
}
