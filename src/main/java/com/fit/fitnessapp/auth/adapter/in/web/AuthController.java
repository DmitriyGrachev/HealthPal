package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.application.port.in.RegisterUserPort;
import com.fit.fitnessapp.auth.application.service.LoginService;
import com.fit.fitnessapp.auth.domain.LoginRequest;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUserPort registerUserPort;
    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.status(HttpStatus.OK).body(loginService.userLogin(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        registerUserPort.registerUser(registerRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse("registered", "User registered successfully"));
    }
}
