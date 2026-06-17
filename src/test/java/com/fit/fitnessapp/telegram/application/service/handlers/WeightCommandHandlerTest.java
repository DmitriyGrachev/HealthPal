package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeightCommandHandlerTest {

    @Mock
    private TelegramBotService botService;
    @Mock
    private TelegramUserRepository telegramUserRepository;
    @Mock
    private ConversationStateUseCase stateUseCase;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WeightCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WeightCommandHandler(botService, telegramUserRepository, stateUseCase, eventPublisher);
    }

    @Test
    void canHandle_ShouldReturnTrueForWeightCommand() {
        Update update = createTextUpdate("/weight");
        when(stateUseCase.getState(anyLong())).thenReturn(ConversationState.IDLE);
        
        assertThat(handler.canHandle(update)).isTrue();
    }

    @Test
    void canHandle_ShouldReturnTrueWhenWaitingWeight() {
        Update update = createTextUpdate("75.5");
        when(stateUseCase.getState(anyLong())).thenReturn(ConversationState.WAITING_WEIGHT);
        
        assertThat(handler.canHandle(update)).isTrue();
    }

    @Test
    void handle_ShouldStartWeightFlow_WhenCommandReceived() {
        Update update = createFullMessageUpdate("/weight");
        Long telegramId = 123L;
        Long chatId = 456L;
        
        TelegramUserEntity user = new TelegramUserEntity();
        user.setTelegramId(telegramId);
        user.setUserId(1L);
        
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(user));
        when(stateUseCase.getState(chatId)).thenReturn(ConversationState.IDLE);

        handler.handle(update);

        verify(stateUseCase).updateState(chatId, ConversationState.WAITING_WEIGHT);
        verify(botService).sendMessage(eq(chatId), contains("enter your current weight"));
    }

    @Test
    void handle_ShouldRecordWeightAndPublishEvent_WhenInWaitingState() {
        Update update = createFullMessageUpdate("82,5"); // Testing comma handling
        Long telegramId = 123L;
        Long chatId = 456L;
        Long userId = 1L;
        
        TelegramUserEntity user = new TelegramUserEntity();
        user.setTelegramId(telegramId);
        user.setUserId(userId);
        
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(user));
        when(stateUseCase.getState(chatId)).thenReturn(ConversationState.WAITING_WEIGHT);

        handler.handle(update);

        ArgumentCaptor<TelegramWeightRequestedEvent> eventCaptor = ArgumentCaptor.forClass(TelegramWeightRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        TelegramWeightRequestedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.weightKg()).isEqualByComparingTo("82.5");
        
        verify(botService).sendMessage(eq(chatId), contains("82.5 kg recorded"));
        verify(stateUseCase).clearState(chatId);
    }

    @Test
    void handle_ShouldShowError_WhenInvalidWeightProvided() {
        Update update = createFullMessageUpdate("invalid");
        Long telegramId = 123L;
        Long chatId = 456L;
        
        TelegramUserEntity user = new TelegramUserEntity();
        user.setTelegramId(telegramId);
        user.setUserId(1L);
        
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(user));
        when(stateUseCase.getState(chatId)).thenReturn(ConversationState.WAITING_WEIGHT);

        handler.handle(update);

        verify(botService).sendMessage(eq(chatId), contains("Invalid format"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private Update createTextUpdate(String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(456L);
        
        return update;
    }

    private Update createFullMessageUpdate(String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        
        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(456L);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123L);
        
        return update;
    }
}
