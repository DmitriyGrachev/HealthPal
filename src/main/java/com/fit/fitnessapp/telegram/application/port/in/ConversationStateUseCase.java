package com.fit.fitnessapp.telegram.application.port.in;

import com.fit.fitnessapp.telegram.domain.ConversationState;
import java.util.Map;

public interface ConversationStateUseCase {
    ConversationState getState(Long chatId);
    void updateState(Long chatId, ConversationState state);
    void updateState(Long chatId, ConversationState state, Map<String, Object> data);
    Map<String, Object> getData(Long chatId);
    void clearState(Long chatId);
}
