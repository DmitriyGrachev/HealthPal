package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.out.UserNotePersistencePort;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDataLifecycleServiceTest {

    @Mock
    private UserNotePersistencePort userNotePersistencePort;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private UserDataLifecycleService service;

    @Test
    @DisplayName("Should assemble data export for user")
    void exportUserData_Success() {
        when(jdbcTemplate.queryForList(contains("SELECT username"), eq(1L)))
                .thenReturn(List.of(Map.of(
                        "username", "testuser",
                        "email", "test@example.com",
                        "registered_at", Timestamp.valueOf(LocalDateTime.now())
                )));
        when(jdbcTemplate.queryForObject(contains("fatsecret_connection"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);
        when(jdbcTemplate.query(contains("telegram_users"), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq(1L)))
                .thenReturn(List.of(123456789L));
        when(userNotePersistencePort.findByUserId(1L)).thenReturn(List.of(
                new UserNoteDto(10L, 1L, LocalDate.now(), "Test note", UserNoteDto.NoteType.GENERAL)
        ));
        when(jdbcTemplate.queryForObject(contains("fatsecret_day"), eq(Integer.class), eq(1L))).thenReturn(5);
        when(jdbcTemplate.queryForObject(contains("workout"), eq(Integer.class), eq(1L))).thenReturn(3);
        when(jdbcTemplate.queryForObject(contains("workout_cardio"), eq(Integer.class), eq(1L))).thenReturn(2);

        UserDataExportDto export = service.exportUserData(1L);

        assertThat(export.userId()).isEqualTo(1L);
        assertThat(export.username()).isEqualTo("testuser");
        assertThat(export.email()).isEqualTo("test@example.com");
        assertThat(export.fatSecretConnected()).isTrue();
        assertThat(export.telegramLinked()).isTrue();
        assertThat(export.telegramId()).isEqualTo(123456789L);
        assertThat(export.notes()).hasSize(1);
        assertThat(export.totalNutritionDays()).isEqualTo(5);
        assertThat(export.totalWorkouts()).isEqualTo(3);
        assertThat(export.totalCardioSessions()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should delete account and cleanup user data")
    void deleteAccount_Success() {
        when(jdbcTemplate.queryForObject(contains("SELECT EXISTS(SELECT 1 FROM users"), eq(Boolean.class), eq(1L)))
                .thenReturn(true);

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
