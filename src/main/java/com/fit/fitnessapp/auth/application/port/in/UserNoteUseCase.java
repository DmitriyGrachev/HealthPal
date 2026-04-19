package com.fit.fitnessapp.auth.application.port.in;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.UserNote;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import java.time.LocalDate;
import java.util.List;

public interface UserNoteUseCase {
    UserNoteDto createNote(UserNoteDto note);
    List<UserNoteDto> getNotesByUserId(Long userId);
    List<UserNoteDto> getNotesByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);
    void deleteNote(Long noteId);
}
