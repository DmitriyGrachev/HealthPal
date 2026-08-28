package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkBindingService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkCommandHandlerTest {

    private final TelegramBotService bot = mock(TelegramBotService.class);
    private final TelegramUserRepository users = mock(TelegramUserRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private TelegramLinkCodeManager codes;
    private LinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        codes = spy(new TelegramLinkCodeManager(jdbc));
        var links = new TelegramLinkBindingService(codes, users,
                Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC));
        handler = new LinkCommandHandler(bot, links);
    }

    @Test
    void locksRepeatedInvalidAttemptsBeforeAnotherCodeLookup() {
        for (int attempt = 0; attempt < 5; attempt++) {
            handler.handle(command(456L, 123L, "00000" + attempt));
        }
        clearInvocations(bot, codes, users);

        handler.handle(command(456L, 123L, "999999"));

        verify(codes, never()).consumeCode(any());
        verify(users, never()).save(any());
        verify(bot).sendMessage(456L, TelegramMessages.LINK_RATE_LIMITED);
    }

    @Test
    void consumesCodeOnceAndQueuesSuccessOnlyForNewCurrentLink() {
        String code = "123456";
        when(jdbc.query(contains("telegram_link_codes"),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq(TelegramLinkCodeManager.hashCode(code)), any(Timestamp.class)))
                .thenReturn(List.of(42L))
                .thenReturn(Collections.emptyList());

        handler.handle(command(456L, 123L, code));
        handler.handle(command(789L, 321L, code));

        ArgumentCaptor<TelegramUserEntity> saved = ArgumentCaptor.forClass(TelegramUserEntity.class);
        verify(users, times(1)).save(saved.capture());
        assertThat(saved.getValue().getTelegramId()).isEqualTo(123L);
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getChatId()).isEqualTo(456L);
        verify(bot).enqueueOwnedMessage(42L, 456L, TelegramMessages.LINK_SUCCESS);
        verify(bot).sendMessage(789L, TelegramMessages.LINK_INVALID);
    }

    private static InboundCommand command(Long chatId, Long senderId, String payload) {
        return new InboundCommand(1, chatId, senderId, null, InboundCommand.Type.LINK, payload);
    }
}
