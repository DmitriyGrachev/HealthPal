package com.fit.fitnessapp.auth.api;

public record UserNoteDeletedEvent(
        Long noteId,
        Long userId
) {}
