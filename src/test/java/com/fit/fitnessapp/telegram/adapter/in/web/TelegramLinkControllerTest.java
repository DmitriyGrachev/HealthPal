package com.fit.fitnessapp.telegram.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramLinkControllerTest {

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private TelegramLinkCodeManager codeManager;

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @InjectMocks
    private TelegramLinkController controller;

    @BeforeEach
    void setUp() {
        when(currentUserApi.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    @DisplayName("Should generate link code for current user")
    void generateLinkCode_Success() {
        when(codeManager.generateCode(1L)).thenReturn("123456");

        ResponseEntity<Map<String, Object>> response = controller.generateLinkCode();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", "123456");
        assertThat(response.getBody()).containsEntry("expiresInMinutes", 10);
    }

    @Test
    @DisplayName("Should return link status when linked")
    void getLinkStatus_Linked() {
        TelegramUserEntity entity = TelegramUserEntity.builder()
                .telegramId(999L)
                .userId(1L)
                .chatId(888L)
                .linkedAt(OffsetDateTime.now())
                .build();
        when(telegramUserRepository.findByUserId(1L)).thenReturn(Optional.of(entity));

        ResponseEntity<Map<String, Object>> response = controller.getLinkStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("linked", true);
        assertThat(response.getBody()).containsEntry("telegramId", 999L);
    }

    @Test
    @DisplayName("Should unlink telegram connection for current user")
    void unlinkTelegram_Success() {
        ResponseEntity<Map<String, Object>> response = controller.unlinkTelegram();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(telegramUserRepository).deleteByUserId(1L);
    }
}
