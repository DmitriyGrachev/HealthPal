package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.ConversationStateEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.ConversationStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationStateService implements ConversationStateUseCase {

    private final ConversationStateRepository repository;

    @Override
    @Transactional(readOnly = true)
    public ConversationState getState(Long chatId) {
        return repository.findById(chatId)
                .map(ConversationStateEntity::getState)
                .orElse(ConversationState.IDLE);
    }

    @Override
    @Transactional
    public void updateState(Long chatId, ConversationState state) {
        updateState(chatId, state, null);
    }

    @Override
    @Transactional
    public void updateState(Long chatId, ConversationState state, Map<String, Object> data) {
        ConversationStateEntity entity = repository.findById(chatId)
                .orElse(ConversationStateEntity.builder()
                        .chatId(chatId)
                        .build());
        
        entity.setState(state);
        if (data != null) {
            if (entity.getData() == null) {
                entity.setData(new HashMap<>(data));
            } else {
                entity.getData().putAll(data);
            }
        }
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getData(Long chatId) {
        return repository.findById(chatId)
                .map(ConversationStateEntity::getData)
                .orElse(new HashMap<>());
    }

    @Override
    @Transactional
    public void clearState(Long chatId) {
        repository.deleteById(chatId);
    }
}
