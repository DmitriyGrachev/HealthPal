package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import com.fit.fitnessapp.auth.application.port.out.UserIdentityLifecyclePort;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import com.fit.fitnessapp.auth.domain.UserIdentityData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataLifecycleServiceTest {

    @Mock
    private UserIdentityLifecyclePort userIdentityPort;

    @Mock
    private UserDataLifecycleParticipant nutritionParticipant;

    @Mock
    private UserDataLifecycleParticipant aiParticipant;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private UserDataLifecycleService service;
    private UserIdentityData user;

    @BeforeEach
    void setUp() {
        user = new UserIdentityData(42L, "test@example.com", "testuser");
        when(nutritionParticipant.key()).thenReturn("nutrition");
        when(aiParticipant.key()).thenReturn("ai");
        service = new UserDataLifecycleService(
                userIdentityPort,
                List.of(nutritionParticipant, aiParticipant),
                clock);
    }

    @Test
    void exportCombinesStableParticipantFragmentsAndAddsUserAiBudget() {
        Map<String, Object> budgetRow = Map.of(
                "scope_type", "USER",
                "scope_id", 42L,
                "used_tokens", 250L);
        when(userIdentityPort.findById(42L)).thenReturn(Optional.of(user));
        when(aiParticipant.exportData(42L)).thenReturn(new UserDataExportFragment("ai", Map.of(
                "aiInsights", List.of(Map.of("insight_type", "daily")),
                "aiUsageBudget", List.of(budgetRow))));
        when(nutritionParticipant.exportData(42L)).thenReturn(new UserDataExportFragment("nutrition", Map.of(
                "profile", Map.of("goal_weight_kg", 75.0),
                "fatSecretConnected", true)));

        UserDataExportDto export = service.exportUserData(42L);

        assertThat(export.userId()).isEqualTo(42L);
        assertThat(export.username()).isEqualTo("testuser");
        assertThat(export.email()).isEqualTo("test@example.com");
        assertThat(export.profile()).containsEntry("goal_weight_kg", 75.0);
        assertThat(export.aiInsights()).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("insight_type", "daily"));
        assertThat(export.aiUsageBudget()).containsExactly(budgetRow);
        assertThat(export.fatSecretConnected()).isTrue();

        InOrder order = inOrder(aiParticipant, nutritionParticipant);
        order.verify(aiParticipant).exportData(42L);
        order.verify(nutritionParticipant).exportData(42L);
    }

    @Test
    void deleteRunsAllModuleCleanupBeforeDeletingAuthIdentity() {
        when(userIdentityPort.findById(42L)).thenReturn(Optional.of(user));
        clearInvocations(aiParticipant, nutritionParticipant, userIdentityPort);

        UserAccountDeletionResult result = service.deleteAccount(42L);

        assertThat(result.success()).isTrue();
        InOrder order = inOrder(aiParticipant, nutritionParticipant, userIdentityPort);
        order.verify(aiParticipant).deleteData(42L);
        order.verify(nutritionParticipant).deleteData(42L);
        order.verify(userIdentityPort).deleteById(42L);
    }

    @Test
    void disconnectDelegatesToModuleOwnersWithoutDeletingNutritionHistory() {
        when(userIdentityPort.findById(42L)).thenReturn(Optional.of(user));

        service.disconnectFatSecret(42L);

        verify(aiParticipant).disconnectExternalAccount(42L);
        verify(nutritionParticipant).disconnectExternalAccount(42L);
    }
}
