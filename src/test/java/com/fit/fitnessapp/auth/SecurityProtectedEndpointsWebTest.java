package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.analytics.application.MonthlyReportOrchestrator;
import com.fit.fitnessapp.analytics.application.WeeklyReportOrchestrator;
import com.fit.fitnessapp.analytics.port.in.MonthlyReportController;
import com.fit.fitnessapp.analytics.port.in.WeeklyReportController;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.workout.adapter.in.web.WorkoutImportController;
import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportWarning;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        WeeklyReportController.class,
        MonthlyReportController.class,
        WorkoutImportController.class
})
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        TokenFilter.class,
        JwtCore.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class SecurityProtectedEndpointsWebTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private WeeklyReportOrchestrator weeklyReportOrchestrator;
    @MockitoBean private MonthlyReportOrchestrator monthlyReportOrchestrator;
    @MockitoBean private ImportWorkoutUseCase importWorkoutUseCase;
    @MockitoBean private CurrentUserApi currentUserApi;

    @Test
    void weeklyReportBatchEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/week"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        stubUser("user", "USER");

        mockMvc.perform(get("/api/v1/week")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(weeklyReportOrchestrator);

        stubUser("admin", "ADMIN");

        mockMvc.perform(get("/api/v1/week")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("admin")))
                .andExpect(status().isOk());

        verify(weeklyReportOrchestrator).generateWeeklyReports();
    }

    @Test
    void monthlyReportBatchEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/month"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        stubUser("user", "USER");

        mockMvc.perform(get("/api/v1/month")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(monthlyReportOrchestrator);

        stubUser("admin", "ADMIN");

        mockMvc.perform(get("/api/v1/month")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("admin")))
                .andExpect(status().isOk());

        verify(monthlyReportOrchestrator).generateMonthlyReports();
    }

    @Test
    void workoutImportEndpointIsVipOnly() throws Exception {
        mockMvc.perform(multipart("/api/v1/workout-import/import/jefit")
                        .file(workoutFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        stubUser("user", "USER");

        mockMvc.perform(multipart("/api/v1/workout-import/import/jefit")
                        .file(workoutFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(currentUserApi, importWorkoutUseCase);

        stubUser("vip", "VIP");
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(importWorkoutUseCase.importWorkouts(any(InputStream.class), eq("jefit"), eq(42L)))
                .thenReturn(WorkoutImportResult.from(
                        List.of(),
                        List.of(new WorkoutImportWarning("WORKOUT SESSIONS", 4, "invalid workout session number"))));

        mockMvc.perform(multipart("/api/v1/workout-import/import/jefit")
                        .file(workoutFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("vip")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.format").value("jefit"))
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.changedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.warnings[0].section").value("WORKOUT SESSIONS"));

        verify(currentUserApi).getCurrentUserId();
        verify(importWorkoutUseCase).importWorkouts(any(InputStream.class), eq("jefit"), eq(42L));
    }

    @Test
    void staleWorkoutImportMatcherDoesNotExposeOldPath() throws Exception {
        stubUser("vip", "VIP");

        mockMvc.perform(multipart("/api/import/jefit")
                        .file(workoutFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("vip")))
                .andExpect(status().isNotFound());

        verify(importWorkoutUseCase, never()).importWorkouts(any(InputStream.class), eq("jefit"), eq(42L));
    }

    private void stubUser(String username, String... roles) {
        when(userDetailsService.loadUserByUsername(username))
                .thenReturn(User.withUsername(username).password("n/a").roles(roles).build());
    }

    private String bearerTokenFor(String subject) {
        return "Bearer " + tokenFor(subject, 3_600_000);
    }

    private String tokenFor(String subject, long expiresInMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMillis))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
                .compact();
    }

    private MockMultipartFile workoutFile() {
        return new MockMultipartFile(
                "file",
                "workouts.csv",
                "text/csv",
                "Date,Exercise,Reps\n2026-07-04,Squat,5\n".getBytes(StandardCharsets.UTF_8));
    }
}
