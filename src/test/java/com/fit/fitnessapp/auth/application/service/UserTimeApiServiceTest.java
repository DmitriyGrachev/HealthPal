package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.adapter.out.persistence.UserTimeApiAdapter;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserTimeApiServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T23:30:00Z"), ZoneOffset.UTC);
    private final UserTimeApiAdapter service = new UserTimeApiAdapter(userRepository, clock);

    @Test
    void resolvesCurrentDateInUsersTimezone() {
        User user = new User();
        user.setTimeZone("Europe/Kyiv");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThat(service.currentDate(42L)).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void updatesOnlyWithValidIanaTimezone() {
        User user = new User();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        service.setTimeZone(42L, "America/New_York");

        assertThat(user.getTimeZone()).isEqualTo("America/New_York");
        verify(userRepository).findById(42L);
        verify(userRepository).save(user);
        assertThatThrownBy(() -> service.setTimeZone(42L, "not/a-zone"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(userRepository);
    }
}
