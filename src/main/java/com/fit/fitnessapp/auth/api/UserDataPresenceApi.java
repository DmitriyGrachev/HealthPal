package com.fit.fitnessapp.auth.api;

public interface UserDataPresenceApi {

    boolean userExists(Long userId);

    boolean userNoteExists(Long userId, Long noteId);
}
