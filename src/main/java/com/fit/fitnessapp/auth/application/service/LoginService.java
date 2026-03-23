package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.in.LoginUseCase;
import com.fit.fitnessapp.auth.domain.LoginRequest;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {
    private final AuthenticationManager authenticationManager;
    private final JwtCore jwtCore;

    @Override
    public String userLogin(LoginRequest loginRequest) {
            // Spring сам проверит логин и пароль
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Если всё ок - генерим токен
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            //TODO after add some validation logic and throw new BadCredentialsException
        return jwtCore.generateToken(userDetails);
    }
}
