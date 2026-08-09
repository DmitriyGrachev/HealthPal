package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.application.service.UserTimeService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class UserTimeApiAdapter implements UserTimeApi {

    private final UserRepository userRepository;
    private final Clock clock;
    private final UserTimeService userTimeService = new UserTimeService();

    public UserTimeApiAdapter(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public LocalDate currentDate(Long userId) {
        return userTimeService.toUserLocalDate(clock.instant(), getTimeZone(userId));
    }

    @Override
    public String getTimeZone(Long userId) {
        return findUser(userId).getTimeZone();
    }

    @Override
    public void setTimeZone(Long userId, String ianaTimeZone) {
        if (ianaTimeZone == null || ianaTimeZone.isBlank()) {
            throw new IllegalArgumentException("IANA timezone must not be blank");
        }
        String normalized = ianaTimeZone.trim();
        userTimeService.resolveUserZoneIdStrict(normalized);
        User user = findUser(userId);
        user.setTimeZone(normalized);
        userRepository.save(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
