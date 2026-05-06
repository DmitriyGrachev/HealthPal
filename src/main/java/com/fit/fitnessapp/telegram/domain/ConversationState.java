package com.fit.fitnessapp.telegram.domain;

/**
 * Represents the current step in a multi-turn conversation.
 */
public enum ConversationState {
    IDLE,
    WAITING_LINK_CODE,
    WAITING_NOTE_TYPE,
    WAITING_NOTE_CONTENT,
    WAITING_CHECKIN,
    WAITING_WEIGHT
}
