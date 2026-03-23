package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.Role;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserPersistencePort {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void registerUser(RegisterRequest registerRequest){
        if(userRepository.existsUserByUsername(registerRequest.username())){
            throw new UserAlreadyExistsException("User with such username already exists");
        }
        if(userRepository.existsUserByEmail(registerRequest.email())){
            throw new UserAlreadyExistsException("User with such email already exists");
        }

        User user = new User();
        user.setUsername(registerRequest.username());
        user.setPassword(passwordEncoder.encode(registerRequest.password()));
        user.setEmail(registerRequest.email());
        user.setRoles(Set.of(Role.USER));
        userRepository.save(user);
    }
}
