package com.fit.fitnessapp.auth.adapter.in;

import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.api.TelegramNoteRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TelegramNoteListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramNoteListener.class);

    private final UserNoteUseCase userNoteUseCase;
    private final UserTimeApi userTimeApi;

    @ApplicationModuleListener
    public void onNoteRequested(TelegramNoteRequestedEvent event) {
        log.info("Auth module received NoteRequestedEvent for user {}", event.userId());

        UserNoteDto dto = new UserNoteDto(
                null,
                event.userId(),
                userTimeApi.currentDate(event.userId()),
                event.content(),
                UserNoteDto.NoteType.valueOf(event.type())
        );

        userNoteUseCase.createNote(dto);
        log.info("Saved note for user {}", event.userId());
    }
}
