package com.fit.fitnessapp.job.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.exception.GlobalExceptionHandler;
import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.ai.RateLimiterService;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(DurableJobController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TokenFilter.class, JwtCore.class,
        ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class DurableJobControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DurableJobUseCase durableJobUseCase;
    @MockitoBean private CurrentUserApi currentUserApi;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private RateLimiterService rateLimiterService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void runningRetryIsAStableConflict() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(durableJobUseCase.getJob(10L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(durableJobUseCase.retryJob(10L)).thenReturn(false);

        mockMvc.perform(post("/api/v1/jobs/10/retry").with(user("me").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DURABLE_JOB_RETRY_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void succeededRetryIsRejectedWithoutMutation() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(durableJobUseCase.getJob(10L)).thenReturn(Optional.of(job(JobStatus.SUCCEEDED)));

        mockMvc.perform(post("/api/v1/jobs/10/retry").with(user("me").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DURABLE_JOB_RETRY_NOT_ALLOWED"));
        verify(durableJobUseCase, never()).retryJob(eq(10L));
    }

    @Test
    void failedRetryReturnsPendingAfterSuccessfulOwnerScopedMutation() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(durableJobUseCase.getJob(10L)).thenReturn(Optional.of(job(JobStatus.FAILED)));
        when(durableJobUseCase.retryJob(10L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/jobs/10/retry").with(user("me").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(durableJobUseCase).retryJob(eq(10L));
    }

    @Test
    void skippedRetryReturnsPendingAfterSuccessfulOwnerScopedMutation() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(durableJobUseCase.getJob(10L)).thenReturn(Optional.of(job(JobStatus.SKIPPED)));
        when(durableJobUseCase.retryJob(10L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/jobs/10/retry").with(user("me").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(durableJobUseCase).retryJob(eq(10L));
    }

    @Test
    void retryCannotCrossUserBoundary() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        when(durableJobUseCase.getJob(10L)).thenReturn(Optional.of(job(JobStatus.FAILED)));

        mockMvc.perform(post("/api/v1/jobs/10/retry").with(user("me").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(durableJobUseCase, never()).retryJob(eq(10L));
    }

    @Test
    void unauthenticatedRetryIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/10/retry"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private DurableJobDto job(JobStatus status) {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        return new DurableJobDto(10L, "TEST_JOB", 42L, status, 1, 3,
                null, null, "{}", "key", now, now);
    }
}
