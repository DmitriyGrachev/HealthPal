package com.fit.fitnessapp.auth.adapter.in;

import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.telegram.api.TelegramNoteRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNoteListener {

    private final UserNoteUseCase userNoteUseCase;

    @ApplicationModuleListener
    public void onNoteRequested(TelegramNoteRequestedEvent event) {
        log.info("Auth module received NoteRequestedEvent for user {}", event.userId());

        UserNoteDto dto = new UserNoteDto(
                null,
                event.userId(),
                LocalDate.now(),
                event.content(),
                UserNoteDto.NoteType.valueOf(event.type())
        );

        userNoteUseCase.createNote(dto);
        log.info("Saved note for user {}: {}", event.userId(), event.content());
    }
}
