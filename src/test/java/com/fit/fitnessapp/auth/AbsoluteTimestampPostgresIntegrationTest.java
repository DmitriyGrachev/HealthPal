package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.out.persistence.UserNoteJpaRepository;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.WeightHistoryJpaRepository;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AbsoluteTimestampPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserNoteJpaRepository noteRepository;

    @Autowired
    private WeightHistoryJpaRepository weightRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void absoluteTimestampsRoundTripAcrossDstOverlapWithoutLosingTheInstant() {
        Instant firstOccurrence = Instant.parse("2026-11-01T05:30:00Z");
        Instant secondOccurrence = Instant.parse("2026-11-01T06:30:00Z");
        long userId = insertUser(firstOccurrence);

        Long firstNoteId = insertNote(userId, firstOccurrence);
        Long secondNoteId = insertNote(userId, secondOccurrence);
        Long weightId = jdbc.queryForObject("""
                INSERT INTO weight_history(user_id, weight_kg, weight_date, weight_source, created_at)
                VALUES (?, 80.0, DATE '2026-11-01', 'MANUAL', ?) RETURNING id
                """, Long.class, userId, Timestamp.from(secondOccurrence));

        assertThat(noteRepository.findById(firstNoteId).orElseThrow().getCreatedAt())
                .isEqualTo(firstOccurrence);
        assertThat(noteRepository.findById(secondNoteId).orElseThrow().getCreatedAt())
                .isEqualTo(secondOccurrence);
        assertThat(weightRepository.findById(weightId).orElseThrow().getCreatedAt())
                .isEqualTo(secondOccurrence);
        assertThat(userRepository.findById(userId).orElseThrow().getRegisteredAt())
                .isEqualTo(firstOccurrence);

        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getTimeZone()).isEqualTo("UTC");
        user.setTimeZone("America/New_York");
        userRepository.saveAndFlush(user);
        assertThat(userRepository.findById(userId).orElseThrow().getTimeZone())
                .isEqualTo("America/New_York");
    }

    private long insertUser(Instant registeredAt) {
        return jdbc.queryForObject("""
                INSERT INTO users(username, email, password, registered_at)
                VALUES ('time-user', 'time-user@example.com', 'hash', ?) RETURNING id
                """, Long.class, Timestamp.from(registeredAt));
    }

    private Long insertNote(long userId, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO user_notes(user_id, related_date, content, type, created_at, updated_at)
                VALUES (?, DATE '2026-11-01', 'DST overlap', 'GENERAL', ?, ?) RETURNING id
                """, Long.class, userId, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }
}
