package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.application.port.in.RegisterUserPort;
import com.fit.fitnessapp.auth.application.service.LoginService;
import com.fit.fitnessapp.auth.domain.LoginRequest;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final RegisterUserPort registerUserPort;//port for registering user
    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try{
            return ResponseEntity.status(HttpStatus.OK).body(loginService.userLogin(loginRequest));
        }catch (BadCredentialsException e){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Wrong login or password");
        }
    }
    //Чуть позже добавлю валидацию
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest){
        try{

            registerUserPort.registerUser(registerRequest);

            return ResponseEntity.ok("Registered successfully!");
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
