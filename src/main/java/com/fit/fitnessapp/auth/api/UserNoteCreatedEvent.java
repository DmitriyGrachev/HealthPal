package com.fit.fitnessapp.auth.api;

import com.fit.fitnessapp.auth.domain.UserNoteDto;
import java.time.LocalDate;

public record UserNoteCreatedEvent(
    Long userId,
    LocalDate date,
    String content,
    UserNoteDto.NoteType type
) {}
