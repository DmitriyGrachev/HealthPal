package com.fit.fitnessapp.auth.domain;

import java.time.LocalDate;

public record UserNoteDto(
        Long id,
        Long userId,
        LocalDate relatedDate,
        String content,
        NoteType type
) {
    public enum NoteType {
        ILLNESS, TRAVEL, INJURY, STRESS, ALLERGY, GOAL, PREFERENCE, OTHER
    }
}
