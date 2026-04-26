package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.out.persistence.UserNoteJpaRepository;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.UserNote;
import com.fit.fitnessapp.auth.application.service.UserNoteService;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UserNoteService.class)
@Transactional
class UserNoteServiceTest {

    @Autowired
    private UserNoteService userNoteService;

    @Autowired
    private UserNoteJpaRepository userNoteJpaRepository;

    private UserNoteDto testNote;

    @BeforeEach
    void setUp() {
        testNote = new UserNoteDto(
                null,
                1L,
                LocalDate.of(2026, 4, 15),
                "Felt weak today, skipped leg day",
                UserNoteDto.NoteType.ILLNESS
        );
    }

    @Test
    void createNote_shouldSaveNoteSuccessfully() {
        UserNoteDto result = userNoteService.createNote(testNote);

        assertThat(result.id()).isNotNull();
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.content()).isEqualTo("Felt weak today, skipped leg day");
        assertThat(result.type()).isEqualTo(UserNoteDto.NoteType.ILLNESS);
    }

    @Test
    void getNotesByUserId_shouldReturnNotesForUser() {
        userNoteService.createNote(testNote);

        List<UserNoteDto> results = userNoteService.getNotesByUserId(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("Felt weak today, skipped leg day");
    }

    @Test
    void getNotesByUserId_shouldReturnEmptyListWhenNoNotes() {
        List<UserNoteDto> results = userNoteService.getNotesByUserId(999L);

        assertThat(results).isEmpty();
    }

    @Test
    void getNotesByUserIdAndDateRange_shouldReturnNotesInRange() {
        userNoteService.createNote(testNote);
        UserNoteDto note2 = new UserNoteDto(
                null, 1L, LocalDate.of(2026, 4, 10), "Travel day", UserNoteDto.NoteType.TRAVEL
        );
        userNoteService.createNote(note2);

        List<UserNoteDto> results = userNoteService.getNotesByUserIdAndDateRange(
                1L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20)
        );

        assertThat(results).hasSize(2);
    }

    @Test
    void getNotesByUserIdAndDateRange_shouldReturnEmptyWhenNoNotesInRange() {
        userNoteService.createNote(testNote);

        List<UserNoteDto> results = userNoteService.getNotesByUserIdAndDateRange(
                1L,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );

        assertThat(results).isEmpty();
    }

    @Test
    void deleteNote_shouldRemoveNoteSuccessfully() {
        UserNoteDto saved = userNoteService.createNote(testNote);

        userNoteService.deleteNote(saved.id());

        Optional<UserNote> result = userNoteJpaRepository.findById(saved.id());
        assertThat(result).isEmpty();
    }

    @Test
    void deleteNote_shouldNotThrowWhenNoteNotFound() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> userNoteService.deleteNote(999L)
        );
    }

    @Test
    void createNote_shouldSaveWithAllNoteTypes() {
        UserNoteDto.NoteType[] types = {
                UserNoteDto.NoteType.ILLNESS,
                UserNoteDto.NoteType.TRAVEL,
                UserNoteDto.NoteType.INJURY,
                UserNoteDto.NoteType.STRESS,
                UserNoteDto.NoteType.OTHER
        };

        for (UserNoteDto.NoteType type : types) {
            UserNoteDto note = new UserNoteDto(
                    null, 1L, LocalDate.now(), "Test note", type
            );
            UserNoteDto result = userNoteService.createNote(note);
            assertThat(result.type()).isEqualTo(type);
        }
    }
}
