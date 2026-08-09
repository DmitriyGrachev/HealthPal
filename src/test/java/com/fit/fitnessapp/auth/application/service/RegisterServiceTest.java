package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegisterServiceTest {

    private final UserPersistencePort userPersistencePort = mock(UserPersistencePort.class);
    private final RegisterService registerService = new RegisterService(userPersistencePort);

    @Test
    void normalizesUsernameAndEmailBeforeDelegatingWhilePreservingPassword() {
        registerService.registerUser(new RegisterRequest("  John Doe  ", " Secret123! ", "  JOHN@Example.COM  "));

        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userPersistencePort).registerUser(requestCaptor.capture());

        assertThat(requestCaptor.getValue()).isEqualTo(
                new RegisterRequest("John Doe", " Secret123! ", "john@example.com"));
    }
}
