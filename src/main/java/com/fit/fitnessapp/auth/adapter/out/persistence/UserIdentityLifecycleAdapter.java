package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.application.port.out.UserIdentityLifecyclePort;
import com.fit.fitnessapp.auth.api.UserDataPresenceApi;
import com.fit.fitnessapp.auth.domain.UserIdentityData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserIdentityLifecycleAdapter implements UserIdentityLifecyclePort, UserDataPresenceApi {

    private final UserRepository userRepository;
    private final UserNoteJpaRepository userNoteRepository;

    @Override
    public Optional<UserIdentityData> findById(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new UserIdentityData(user.getId(), user.getEmail(), user.getUsername()));
    }

    @Override
    public void deleteById(Long userId) {
        userRepository.deleteById(userId);
    }

    @Override
    public boolean userExists(Long userId) {
        return userRepository.existsById(userId);
    }

    @Override
    public boolean userNoteExists(Long userId, Long noteId) {
        return noteId != null && userNoteRepository.findOwnedByIdForUpdate(noteId, userId).isPresent();
    }
}
