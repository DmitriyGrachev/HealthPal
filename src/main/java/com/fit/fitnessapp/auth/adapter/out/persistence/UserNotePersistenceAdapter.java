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
        UserNote entity = UserNote.builder()
                .userId(dto.userId())
                .relatedDate(dto.relatedDate())
                .content(dto.content())
                .type(UserNote.NoteType.valueOf(dto.type().name()))
                .build();

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
    public void deleteById(Long noteId) {
        jpaRepository.deleteById(noteId);
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
