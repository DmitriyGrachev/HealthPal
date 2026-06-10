package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.api.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserAdapter implements UserPort {

    private final UserRepository userRepository;

    @Override
    public List<Long> getUserIdsWithFatSecretTokens() {
        return userRepository.findUserIdsWithFatSecretTokens();
    }

    @Override
    public String getFatSecretAccessTokenByUserId(Long userId) {
        return userRepository.getFatSecretAccessTokenByUserId(userId);
    }

    @Override
    public String getFatSecretAccessTokenSecretByUserId(Long userId) {
        return userRepository.getFatSecretAccessTokenSecretByUserId(userId);
    }
}
