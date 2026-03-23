package com.fit.fitnessapp.auth.application.port.out;

import com.fit.fitnessapp.auth.domain.RegisterRequest;

public interface UserPersistencePort {

    void registerUser(RegisterRequest registerRequest);

}
