package com.fit.fitnessapp.auth.application.port.in;

import com.fit.fitnessapp.auth.domain.LoginRequest;

public interface LoginUseCase {
    String userLogin(LoginRequest loginRequest);
}
