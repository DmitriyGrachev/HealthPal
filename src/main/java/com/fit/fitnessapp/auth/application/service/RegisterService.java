package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.in.RegisterUserPort;
import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.Role;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegisterService implements RegisterUserPort {

    private final UserPersistencePort userPersistencePort;

    public void registerUser(RegisterRequest registerRequest){
        userPersistencePort.registerUser(registerRequest);
    }
}
