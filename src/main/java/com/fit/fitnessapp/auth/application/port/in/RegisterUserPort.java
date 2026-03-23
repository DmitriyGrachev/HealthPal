package com.fit.fitnessapp.auth.application.port.in;


import com.fit.fitnessapp.auth.domain.RegisterRequest;

public interface RegisterUserPort {
     void registerUser(RegisterRequest registerRequest);
}
