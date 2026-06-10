package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.in.RegisterUserPort;
import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterService implements RegisterUserPort {

    private final UserPersistencePort userPersistencePort;

    public void registerUser(RegisterRequest registerRequest){
        userPersistencePort.registerUser(registerRequest);
    }
}
