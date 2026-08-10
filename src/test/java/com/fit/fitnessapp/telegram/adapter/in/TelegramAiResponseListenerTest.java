package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TelegramAiResponseListenerTest {

    @Test
    void delayedAiResponseUsesRevocableOwnedDelivery() {
        TelegramBotService botService = mock(TelegramBotService.class);
        TelegramAiResponseListener listener = new TelegramAiResponseListener(botService);

        listener.onAiResponse(new TelegramAiResponseEvent(42L, 100L, "private response"));

        verify(botService).enqueueOwnedMessage(42L, 100L, "private response");
        verify(botService, never()).sendMessage(anyLong(), anyString());
    }
}
