package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.Role;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPersistenceAdapterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserPersistenceAdapter userPersistenceAdapter = new UserPersistenceAdapter(userRepository, passwordEncoder);

    @Test
    void persistsCanonicalValues() {
        RegisterRequest request = new RegisterRequest("John", "Secret123!", "john@example.com");
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-password");

        userPersistenceAdapter.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User persistedUser = userCaptor.getValue();
        assertThat(persistedUser.getUsername()).isEqualTo("John");
        assertThat(persistedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(persistedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(persistedUser.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void translatesUniqueViolationRaisedDuringSaveAndFlush() {
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userPersistenceAdapter.registerUser(
                new RegisterRequest("John", "Secret123!", "john@example.com")))
                .isInstanceOf(UserAlreadyExistsException.class);
    }
}
