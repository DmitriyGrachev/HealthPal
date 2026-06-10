package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.api.UserNoteCreatedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.application.port.out.UserNotePersistencePort;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNoteService implements UserNoteUseCase {

    private final UserNotePersistencePort userNotePersistencePort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UserNoteDto createNote(UserNoteDto dto) {
        UserNoteDto saved = userNotePersistencePort.save(dto);

        eventPublisher.publishEvent(new UserNoteCreatedEvent(
                saved.userId(),
                saved.relatedDate(),
                saved.content(),
                saved.type()
        ));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserNoteDto> getNotesByUserId(Long userId) {
        return userNotePersistencePort.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserNoteDto> getNotesByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to) {
        return userNotePersistencePort.findByUserIdAndDateRange(userId, from, to);
    }

    @Override
    @Transactional
    public void deleteNote(Long noteId) {
        userNotePersistencePort.deleteById(noteId);
    }
}
