package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataLifecycleServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserDataLifecycleService service;

    @Test
    @DisplayName("Should assemble data export for user")
    void exportUserData_Success() {
        when(jdbcTemplate.queryForList(contains("SELECT id, email, username"), eq(1L)))
                .thenReturn(List.of(Map.of(
                        "username", "testuser",
                        "email", "test@example.com"
                )));
        when(jdbcTemplate.queryForList(contains("SELECT * FROM profile"), eq(1L)))
                .thenReturn(List.of(Map.of("age", 30)));
        when(jdbcTemplate.queryForList(contains("SELECT id, weight_kg"), eq(1L)))
                .thenReturn(List.of(Map.of("weight_kg", 80.0)));
        when(jdbcTemplate.queryForList(contains("SELECT * FROM fatsecret_food_entry"), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT * FROM workout_session"), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT id, note_type"), eq(1L)))
                .thenReturn(List.of(Map.of("content", "Test note")));

        UserDataExportDto export = service.exportUserData(1L);

        assertThat(export.userId()).isEqualTo(1L);
        assertThat(export.username()).isEqualTo("testuser");
        assertThat(export.email()).isEqualTo("test@example.com");
        assertThat(export.profile()).containsEntry("age", 30);
        assertThat(export.weightHistory()).hasSize(1);
    }

    @Test
    @DisplayName("Should delete account and cleanup user data")
    void deleteAccount_Success() {
        when(jdbcTemplate.update(anyString(), eq(1L))).thenReturn(1);

        UserAccountDeletionResult result = service.deleteAccount(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.success()).isTrue();
        verify(jdbcTemplate).update("DELETE FROM users WHERE id = ?", 1L);
    }

    @Test
    @DisplayName("Should disconnect FatSecret for user")
    void disconnectFatSecret_Success() {
        service.disconnectFatSecret(1L);

        verify(jdbcTemplate).update("DELETE FROM fatsecret_connection WHERE user_id = ?", 1L);
    }
}
