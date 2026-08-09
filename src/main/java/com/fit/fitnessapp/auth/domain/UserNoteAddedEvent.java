package com.fit.fitnessapp.auth.domain;

import java.time.LocalDate;

/**
 * Event published when a user adds a new note.
 * FitnessAiService listens to this to save episodic memories.
 */
public record UserNoteAddedEvent(
        Long userId,
        LocalDate relatedDate,
        String content,
        UserNoteDto.NoteType type
) {}
