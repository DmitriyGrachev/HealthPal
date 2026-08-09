package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.api.UserNoteCreatedEvent;
import com.fit.fitnessapp.auth.api.UserNoteDeletedEvent;
import com.fit.fitnessapp.auth.application.port.out.UserNotePersistencePort;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserNoteServiceTest {

    @Test
    void createAndDeletePublishStableNoteLifecycleEvents() {
        UserNotePersistencePort persistence = mock(UserNotePersistencePort.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        UserNoteService service = new UserNoteService(persistence, publisher);
        UserNoteDto saved = new UserNoteDto(
                77L, 42L, LocalDate.of(2026, 7, 6), "peanut allergy", UserNoteDto.NoteType.ALLERGY);
        when(persistence.save(any())).thenReturn(saved);

        service.createNote(saved);
        service.deleteNote(42L, 77L);

        verify(publisher).publishEvent(new UserNoteCreatedEvent(
                77L, 42L, saved.relatedDate(), saved.content(), saved.type()));
        verify(publisher).publishEvent(new UserNoteDeletedEvent(42L, 77L));
    }
}
