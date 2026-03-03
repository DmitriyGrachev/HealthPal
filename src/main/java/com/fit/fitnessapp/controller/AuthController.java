package com.fit.fitnessapp.controller;

import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import com.fit.fitnessapp.model.dto.LoginRequest;
import com.fit.fitnessapp.model.dto.RegisterRequest;
import com.fit.fitnessapp.model.user.User;
import com.fit.fitnessapp.service.UserService;
import com.fit.fitnessapp.utils.JwtCore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtCore jwtCore;
    private final UserService userService; // Твой сервис для регистрации

    //@AuthenticationPrincipal User user
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {

        // Spring сам проверит логин и пароль
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Если всё ок - генерим токен
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        return ResponseEntity.status(HttpStatus.OK).body(jwtCore.generateToken(userDetails));

        } catch (BadCredentialsException e) {
            // Spring превратил твой UsernameNotFoundException в BadCredentialsException
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Wrong login or password");
        }
    }
    //Чуть позже добавлю валидацию
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest){
        try{

            userService.registerUser(registerRequest);

            return ResponseEntity.ok("Registered successfully!");
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
