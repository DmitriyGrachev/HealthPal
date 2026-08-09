package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.UserNote;
import com.fit.fitnessapp.auth.application.port.out.UserNotePersistencePort;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserNotePersistenceAdapter implements UserNotePersistencePort {

    private final UserNoteJpaRepository jpaRepository;

    @Override
    public UserNoteDto save(UserNoteDto dto) {
        UserNote entity = new UserNote();
        entity.setUserId(dto.userId());
        entity.setRelatedDate(dto.relatedDate());
        entity.setContent(dto.content());
        entity.setType(UserNote.NoteType.valueOf(dto.type().name()));

        return toDto(jpaRepository.save(entity));
    }

    @Override
    public List<UserNoteDto> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByRelatedDateDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<UserNoteDto> findByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to) {
        return jpaRepository.findByUserIdAndRelatedDateBetweenOrderByRelatedDateDesc(userId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public void deleteByUserIdAndId(Long userId, Long noteId) {
        jpaRepository.findById(noteId)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(jpaRepository::delete);
    }

    private UserNoteDto toDto(UserNote entity) {
        return new UserNoteDto(
                entity.getId(),
                entity.getUserId(),
                entity.getRelatedDate(),
                entity.getContent(),
                UserNoteDto.NoteType.valueOf(entity.getType().name())
        );
    }
}
