package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.ConversationStateEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.ConversationStateRepository;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStateServiceTest {

    @Mock
    private ConversationStateRepository stateRepository;
    @Mock
    private TelegramUserRepository telegramUserRepository;

    @Test
    void activeLinkPersistsStateWithExplicitOwner() {
        when(telegramUserRepository.findByUserIdAndChatIdForUpdate(7L, 100L))
                .thenReturn(Optional.of(TelegramUserEntity.builder()
                        .telegramId(70L).userId(7L).chatId(100L).build()));
        when(stateRepository.findById(100L)).thenReturn(Optional.empty());
        ConversationStateService service = new ConversationStateService(stateRepository, telegramUserRepository);

        boolean updated = service.updateState(
                7L, 100L, ConversationState.WAITING_NOTE_CONTENT, Map.of("noteType", "GENERAL"));

        assertThat(updated).isTrue();
        ArgumentCaptor<ConversationStateEntity> state = ArgumentCaptor.forClass(ConversationStateEntity.class);
        verify(stateRepository).save(state.capture());
        assertThat(state.getValue().getUserId()).isEqualTo(7L);
        assertThat(state.getValue().getChatId()).isEqualTo(100L);
        assertThat(state.getValue().getState()).isEqualTo(ConversationState.WAITING_NOTE_CONTENT);
        assertThat(state.getValue().getData()).containsEntry("noteType", "GENERAL");
    }

    @Test
    void revokedLinkCannotRecreateConversationState() {
        when(telegramUserRepository.findByUserIdAndChatIdForUpdate(7L, 100L)).thenReturn(Optional.empty());
        ConversationStateService service = new ConversationStateService(stateRepository, telegramUserRepository);

        boolean updated = service.updateState(7L, 100L, ConversationState.WAITING_WEIGHT);

        assertThat(updated).isFalse();
        verify(stateRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
