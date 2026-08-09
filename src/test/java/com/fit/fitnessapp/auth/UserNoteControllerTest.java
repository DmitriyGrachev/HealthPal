package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.in.web.UserNoteController;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserNoteControllerTest {

    @Test
    void createNoteUsesAuthenticatedUserIdInsteadOfRequestBodyUserId() {
        UserNoteUseCase userNoteUseCase = mock(UserNoteUseCase.class);
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        when(userNoteUseCase.createNote(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserNoteController controller = new UserNoteController(userNoteUseCase, currentUserApi);
        UserNoteDto request = new UserNoteDto(
                null,
                999L,
                LocalDate.of(2026, 6, 10),
                "private note",
                UserNoteDto.NoteType.GENERAL
        );

        UserNoteDto created = controller.createNote(request).getBody();

        assertThat(created.userId()).isEqualTo(7L);
        verify(userNoteUseCase).createNote(new UserNoteDto(
                null,
                7L,
                LocalDate.of(2026, 6, 10),
                "private note",
                UserNoteDto.NoteType.GENERAL
        ));
    }

    @Test
    void getUserNotesReadsOnlyAuthenticatedUsersNotes() {
        UserNoteUseCase userNoteUseCase = mock(UserNoteUseCase.class);
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        when(userNoteUseCase.getNotesByUserId(7L)).thenReturn(List.of());

        UserNoteController controller = new UserNoteController(userNoteUseCase, currentUserApi);

        controller.getUserNotes(null, null);

        verify(userNoteUseCase).getNotesByUserId(7L);
    }

    @Test
    void deleteNoteIsScopedToAuthenticatedUser() {
        UserNoteUseCase userNoteUseCase = mock(UserNoteUseCase.class);
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);

        UserNoteController controller = new UserNoteController(userNoteUseCase, currentUserApi);

        controller.deleteNote(42L);

        verify(userNoteUseCase).deleteNote(7L, 42L);
    }
}
