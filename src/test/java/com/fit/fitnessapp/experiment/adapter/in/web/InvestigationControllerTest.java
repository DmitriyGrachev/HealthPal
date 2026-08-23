package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.ai.RateLimiterService;
import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.exception.GlobalExceptionHandler;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationQueryUseCase;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import com.fit.fitnessapp.experiment.domain.InvalidTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestigationController.class)
@AutoConfigureMockMvc
@Import({ExperimentExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class, TokenFilter.class, JwtCore.class,
        ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class InvestigationControllerTest {

    private static final Long USER_ID = 42L;
    private static final String BASE = "/api/v1/investigations";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InvestigationCommandUseCase commands;
    @MockitoBean private InvestigationQueryUseCase queries;
    @MockitoBean private CurrentUserApi currentUserApi;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private RateLimiterService rateLimiterService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void createDerivesOwnerAndAcceptsHeaderIdempotencyKey() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Investigation created = investigation(7L, USER_ID, InvestigationStatus.OPEN, 0L);
        when(commands.create(USER_ID, "Deadlift plateau", "Progress stopped", "create-1")).thenReturn(created);

        mockMvc.perform(post(BASE)
                        .with(user("me").roles("USER"))
                        .header("Idempotency-Key", "create-1")
                        .contentType("application/json")
                        .content("""
                                {"userId":999,"title":"Deadlift plateau","problemStatement":"Progress stopped"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userId").value(USER_ID));

        verify(commands).create(eq(USER_ID), eq("Deadlift plateau"), eq("Progress stopped"), eq("create-1"));
        verify(currentUserApi).getCurrentUserId();
    }

    @Test
    void duplicateCreateReturnsSameResourceAndLocation() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Investigation created = investigation(7L, USER_ID, InvestigationStatus.OPEN, 0L);
        when(commands.create(USER_ID, "Deadlift plateau", "Progress stopped", "create-once"))
                .thenReturn(created);
        String body = "{\"title\":\"Deadlift plateau\",\"problemStatement\":\"Progress stopped\"}";

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "create-once")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "create-once")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Location"))
                        .isEqualTo("/api/v1/investigations/7"));

        verify(commands, org.mockito.Mockito.times(2))
                .create(USER_ID, "Deadlift plateau", "Progress stopped", "create-once");
    }

    @Test
    void createRequiresOneKeyAndRejectsConflictingHeaderAndBody() throws Exception {
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"title\":\"title\",\"problemStatement\":\"problem\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "header-key")
                        .contentType("application/json")
                        .content("{\"title\":\"title\",\"problemStatement\":\"problem\",\"idempotencyKey\":\"body-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void listIsCurrentUserScoped() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(queries.findAll(USER_ID)).thenReturn(List.of(investigation(7L, USER_ID, InvestigationStatus.OPEN, 0L)));

        mockMvc.perform(get(BASE).with(user("me").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));

        verify(queries).findAll(USER_ID);
    }

    @Test
    void crossOwnerGetHasStableNotFound() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(queries.find(USER_ID, 7L)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/7").with(user("me").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));

        verify(queries).find(USER_ID, 7L);
        verify(currentUserApi).getCurrentUserId();
    }

    @Test
    void crossOwnerTransitionHasTheSameStableNotFound() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "missing", null))
                .thenThrow(new ExperimentNotFoundException());

        mockMvc.perform(post(BASE + "/7/transitions").with(user("another-user").roles("USER"))
                        .contentType("application/json")
                        .content("{\"userId\":999,\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":0,\"idempotencyKey\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));

        verify(commands).transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "missing", null);
        verify(currentUserApi).getCurrentUserId();
    }

    @Test
    void transitionPassesTypedCommandVersionKeyAndReason() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Investigation transitioned = investigation(7L, USER_ID, InvestigationStatus.COLLECTING_BASELINE, 1L);
        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "transition-1", "baseline"))
                .thenReturn(transitioned);

        mockMvc.perform(post(BASE + "/7/transitions")
                        .with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("""
                                {"command":"COLLECTING_BASELINE","expectedVersion":0,
                                 "idempotencyKey":"transition-1","reason":"baseline"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTING_BASELINE"))
                .andExpect(jsonPath("$.aggregateVersion").value(1));

        verify(commands).transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "transition-1", "baseline");
    }

    @Test
    void duplicateTransitionCommandReturnsExistingResult() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Investigation existing = investigation(7L, USER_ID, InvestigationStatus.COLLECTING_BASELINE, 1L);
        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "same-key", null)).thenReturn(existing);

        String body = """
                {"command":"COLLECTING_BASELINE","expectedVersion":0,"idempotencyKey":"same-key"}
                """;
        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregateVersion").value(1));
        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(commands, org.mockito.Mockito.times(2))
                .transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "same-key", null);
    }

    @Test
    void versionConflictUsesStableConflictCode() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "stale", null))
                .thenThrow(new AggregateVersionConflictException());

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":0,\"idempotencyKey\":\"stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void illegalTransitionUsesStableConflictCode() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "illegal", null))
                .thenThrow(new InvalidTransitionException("not legal"));

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":0,\"idempotencyKey\":\"illegal\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void transitionNotFoundAndIdempotencyMismatchUseStableCodes() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "missing", null))
                .thenThrow(new ExperimentNotFoundException());
        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":0,\"idempotencyKey\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        when(commands.transition(USER_ID, 7L, "COLLECTING_BASELINE", 0L, "same-key", null))
                .thenThrow(new IdempotencyConflictException());
        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":0,\"idempotencyKey\":\"same-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Idempotency key is already bound to a different command"));
    }

    @Test
    void invalidBoundsUseValidationCode() throws Exception {
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"title\":\"\",\"problemStatement\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":-1,\"idempotencyKey\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(commands, never()).transition(eq(USER_ID), eq(7L), eq("COLLECTING_BASELINE"), eq(-1L), eq("x"), eq(null));
    }

    @Test
    void allRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(post(BASE).contentType("application/json")
                        .content("{\"title\":\"title\",\"problemStatement\":\"problem\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE + "/7")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post(BASE + "/7/transitions").contentType("application/json")
                        .content("{\"command\":\"COLLECTING_BASELINE\",\"expectedVersion\":0,\"idempotencyKey\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static Investigation investigation(Long id, Long userId, InvestigationStatus status, long version) {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        return new Investigation(id, userId, "Deadlift plateau", "Progress stopped", status, version, now, now);
    }
}
