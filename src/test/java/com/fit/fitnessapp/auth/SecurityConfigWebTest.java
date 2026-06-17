package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.ai.RateLimitInterceptor;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigWebTest.TestEndpoints.class)
@Import({SecurityConfig.class, TokenFilter.class, JwtCore.class, SecurityConfigWebTest.TestEndpoints.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;

    @Test
    void authEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/register"))
                .andExpect(status().isOk());
    }

    @Test
    void testEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/test/probe"))
                .andExpect(status().isUnauthorized());

        when(userDetailsService.loadUserByUsername("user"))
                .thenReturn(User.withUsername("user").password("n/a").roles("USER").build());

        mockMvc.perform(get("/test/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("user", 3_600_000)))
                .andExpect(status().isForbidden());

        when(userDetailsService.loadUserByUsername("admin"))
                .thenReturn(User.withUsername("admin").password("n/a").roles("ADMIN").build());

        mockMvc.perform(get("/test/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin", 3_600_000)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/protected/probe"))
                .andExpect(status().isUnauthorized());

        when(userDetailsService.loadUserByUsername("user"))
                .thenReturn(User.withUsername("user").password("n/a").roles("USER").build());

        mockMvc.perform(get("/protected/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("user", 3_600_000)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidAndExpiredTokenReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/protected/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/protected/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("user", -1_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/protected/probe")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    private String tokenFor(String subject, long expiresInMillis) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiresInMillis))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")), SignatureAlgorithm.HS256)
                .compact();
    }

    @RestController
    static class TestEndpoints {
        @PostMapping("/auth/login")
        String login() {
            return "login";
        }

        @PostMapping("/auth/register")
        String register() {
            return "register";
        }

        @GetMapping("/test/probe")
        String testProbe() {
            return "test";
        }

        @GetMapping("/protected/probe")
        String protectedProbe() {
            return "protected";
        }
    }
}
