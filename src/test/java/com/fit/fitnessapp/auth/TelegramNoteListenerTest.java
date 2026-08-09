package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.api.TelegramNoteRequestedEvent;
import com.fit.fitnessapp.auth.adapter.in.TelegramNoteListener;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramNoteListenerTest {

    @Test
    void eventListenerPersistsNoteWithTheUserTimezoneDate() {
        UserNoteUseCase notes = mock(UserNoteUseCase.class);
        UserTimeApi userTimeApi = mock(UserTimeApi.class);
        when(userTimeApi.currentDate(42L)).thenReturn(LocalDate.of(2026, 7, 2));
        TelegramNoteListener listener = new TelegramNoteListener(notes, userTimeApi);

        listener.onNoteRequested(new TelegramNoteRequestedEvent(42L, 100L, "felt strong", "TRAINING"));

        verify(notes).createNote(new UserNoteDto(
                null, 42L, LocalDate.of(2026, 7, 2), "felt strong", UserNoteDto.NoteType.TRAINING));
    }
}
