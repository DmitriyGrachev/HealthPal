package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataLifecycleServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private UserDataLifecycleService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new UserDataLifecycleService(jdbcTemplate, clock);
    }

    @Test
    @DisplayName("Should assemble data export using correct table/column names")
    void exportUserData_Success() {
        lenient().when(jdbcTemplate.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT id, email, username FROM users"), eq(1L)))
                .thenReturn(List.of(Map.of(
                        "username", "testuser",
                        "email", "test@example.com"
                )));
        when(jdbcTemplate.queryForList(contains("SELECT * FROM profile"), eq(1L)))
                .thenReturn(List.of(Map.of("age", 30)));
        when(jdbcTemplate.queryForList(contains("weight_history"), eq(1L)))
                .thenReturn(List.of(Map.of("weight_kg", 80.0, "weight_date", "2026-01-01")));
        when(jdbcTemplate.queryForList(contains("fatsecret_food"), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM workout"), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM user_notes"), eq(1L)))
                .thenReturn(List.of(Map.of("content", "Test note")));
        when(jdbcTemplate.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(1L)))
                .thenReturn(false);

        UserDataExportDto export = service.exportUserData(1L);

        assertThat(export.userId()).isEqualTo(1L);
        assertThat(export.username()).isEqualTo("testuser");
        assertThat(export.email()).isEqualTo("test@example.com");
        assertThat(export.profile()).containsEntry("age", 30);
        assertThat(export.weightHistory()).hasSize(1);
    }

    @Test
    @DisplayName("Should delete account using correct table names")
    void deleteAccount_Success() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM users"), eq(Integer.class), eq(1L)))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        UserAccountDeletionResult result = service.deleteAccount(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.success()).isTrue();
        verify(jdbcTemplate).update("DELETE FROM user_memory WHERE metadata->>'user_id' = ?", "1");
        verify(jdbcTemplate).update("DELETE FROM users WHERE id = ?", 1L);
    }

    @Test
    @DisplayName("Should disconnect FatSecret for user")
    void disconnectFatSecret_Success() {
        service.disconnectFatSecret(1L);

        verify(jdbcTemplate).update("DELETE FROM fatsecret_connection WHERE user_id = ?", 1L);
    }
}
