package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.UserNote;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.adapter.out.persistence.UserNoteJpaRepository;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNoteService implements UserNoteUseCase {
    
    private final UserNoteJpaRepository jpaRepository;
    
    @Override
    @Transactional
    public UserNoteDto createNote(UserNoteDto dto) {
        UserNote entity = UserNote.builder()
                .userId(dto.userId())
                .relatedDate(dto.relatedDate())
                .content(dto.content())
                .type(UserNote.NoteType.valueOf(dto.type().name()))
                .build();
        
        UserNote saved = jpaRepository.save(entity);
        return toDto(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UserNoteDto> getNotesByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByRelatedDateDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UserNoteDto> getNotesByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to) {
        return jpaRepository.findByUserIdAndRelatedDateBetweenOrderByRelatedDateDesc(userId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    @Override
    @Transactional
    public void deleteNote(Long noteId) {
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
