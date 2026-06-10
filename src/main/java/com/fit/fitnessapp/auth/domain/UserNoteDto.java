package com.fit.fitnessapp.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserNoteDto(
        Long id,
        Long userId,

        @NotNull
        LocalDate relatedDate,

        @NotBlank
        @Size(max = 500)
        String content,

        @NotNull
        NoteType type
) {
    public enum NoteType {
        ILLNESS, TRAVEL, INJURY, STRESS, ALLERGY, GOAL, PREFERENCE,
        TRAINING, NUTRITION, GENERAL, MOOD, OTHER
    }
}
