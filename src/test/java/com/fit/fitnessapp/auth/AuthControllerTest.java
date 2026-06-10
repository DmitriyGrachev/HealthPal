package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.in.web.AuthController;
import com.fit.fitnessapp.auth.application.port.in.RegisterUserPort;
import com.fit.fitnessapp.auth.application.service.LoginService;
import com.fit.fitnessapp.auth.domain.LoginRequest;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @MockBean private RegisterUserPort registerUserPort;
    @MockBean private LoginService loginService;
    @MockBean private RateLimitInterceptor rateLimitInterceptor;
    @MockBean private TokenFilter tokenFilter;
    @MockBean private UserDetailsService userDetailsService;

    @Autowired private MockMvc mockMvc;

    @Test
    void loginReturnsTokenWhenCredentialsAreValid() throws Exception {
        when(loginService.userLogin(new LoginRequest("john", "secret")))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("jwt-token"));
    }

    @Test
    void loginReturnsUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(loginService.userLogin(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Wrong login or password"));
    }

    @Test
    void registerDelegatesToRegisterUseCase() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","email":"john@example.com","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Registered successfully!"));

        verify(registerUserPort).registerUser(new RegisterRequest("john", "secret", "john@example.com"));
    }

    @Test
    void registerReturnsBadRequestWhenUserAlreadyExists() throws Exception {
        doThrow(new UserAlreadyExistsException("User already exists"))
                .when(registerUserPort)
                .registerUser(any(RegisterRequest.class));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","email":"john@example.com","password":"secret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User already exists"));
    }
}
