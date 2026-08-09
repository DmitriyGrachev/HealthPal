package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.application.port.out.UserNotePersistencePort;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserNoteForeignKeyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserNotePersistencePort userNotePersistencePort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void userNotesRejectMissingUserAndCascadeWhenUserIsDeleted() {
        assertThatThrownBy(() -> userNotePersistencePort.save(noteFor(404_404L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long userId = insertUser();
        UserNoteDto savedNote = userNotePersistencePort.save(noteFor(userId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_notes WHERE id = ?",
                Long.class,
                savedNote.id()))
                .isEqualTo(1L);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_notes WHERE id = ?",
                Long.class,
                savedNote.id()))
                .isZero();
    }

    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (username, email, password)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "note-fk-" + suffix,
                "note-fk-" + suffix + "@example.test",
                "{noop}password");
    }

    private static UserNoteDto noteFor(Long userId) {
        return new UserNoteDto(
                null,
                userId,
                LocalDate.of(2026, 7, 5),
                "FK integration note",
                UserNoteDto.NoteType.GENERAL);
    }
}
