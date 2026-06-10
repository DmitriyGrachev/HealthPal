package com.fit.fitnessapp.auth.application.port.out;

import com.fit.fitnessapp.auth.domain.UserNoteDto;

import java.time.LocalDate;
import java.util.List;

public interface UserNotePersistencePort {

    UserNoteDto save(UserNoteDto dto);

    List<UserNoteDto> findByUserId(Long userId);

    List<UserNoteDto> findByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);

    void deleteByUserIdAndId(Long userId, Long noteId);
}
