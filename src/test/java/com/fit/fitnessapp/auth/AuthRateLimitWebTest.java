package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.ai.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthRateLimitWebTest.AuthEndpoints.class)
@Import({SecurityConfig.class, TokenFilter.class, JwtCore.class, AuthRateLimitWebTest.AuthEndpoints.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.auth-rate-limit.capacity=2",
        "fitness.app.auth-rate-limit.refill-period=PT1M"
})
class AuthRateLimitWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;

    @Test
    void loginAndRegisterAreRateLimited() throws Exception {
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/auth/register"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/register"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/register"))
                .andExpect(status().isTooManyRequests());
    }

    @RestController
    static class AuthEndpoints {
        @PostMapping("/auth/login")
        String login() {
            return "login";
        }

        @PostMapping("/auth/register")
        String register() {
            return "register";
        }
    }
}
