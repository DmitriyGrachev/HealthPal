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
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentQueryUseCase;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentInFlightConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.InvalidTransitionException;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExperimentController.class)
@AutoConfigureMockMvc
@Import({ExperimentExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class, TokenFilter.class,
        JwtCore.class, ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class ExperimentControllerTest {

    private static final Long USER_ID = 42L;
    private static final String BASE = "/api/v1/experiments";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExperimentCommandUseCase commands;
    @MockitoBean private ExperimentQueryUseCase queries;
    @MockitoBean private CurrentUserApi currentUserApi;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private RateLimiterService rateLimiterService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void createDerivesOwnerAndMapsTheSingularIntervention() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Experiment created = experiment(7L, USER_ID, ExperimentStatus.DRAFT, 0L);
        when(commands.create(eq(USER_ID), any(Experiment.class), eq("create-1"))).thenReturn(created);

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "create-1")
                        .contentType("application/json")
                        .content(validBody().replace("\"userId\":999,", "\"userId\":999,")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.intervention.action").value("No late meals"));

        var captor = org.mockito.ArgumentCaptor.forClass(Experiment.class);
        verify(commands).create(eq(USER_ID), captor.capture(), eq("create-1"));
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().investigationId()).isEqualTo(11L);
        assertThat(captor.getValue().goalId()).isEqualTo(12L);
        assertThat(captor.getValue().intervention().action()).isEqualTo("No late meals");
        assertThat(captor.getValue().outcomeDirection().name()).isEqualTo("INCREASE");
        assertThat(captor.getValue().meaningfulChange()).isEqualByComparingTo("1");
    }

    @Test
    void duplicateCreateIsSafeToReplay() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.create(eq(USER_ID), any(Experiment.class), eq("create-once")))
                .thenReturn(experiment(7L, USER_ID, ExperimentStatus.DRAFT, 0L));
        String body = validBody();

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "create-once")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "create-once")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location"))
                        .isEqualTo(BASE + "/7"));

        verify(commands, times(2)).create(eq(USER_ID), any(Experiment.class), eq("create-once"));
    }

    @Test
    void listAndGetAreCurrentUserScoped() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(queries.findAll(USER_ID)).thenReturn(List.of(experiment(7L, USER_ID, ExperimentStatus.DRAFT, 0L)));
        when(queries.find(USER_ID, 7L)).thenReturn(Optional.of(experiment(7L, USER_ID, ExperimentStatus.DRAFT, 0L)));

        mockMvc.perform(get(BASE).with(user("me").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(7));
        mockMvc.perform(get(BASE + "/7").with(user("me").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));

        verify(queries).findAll(USER_ID);
        verify(queries).find(USER_ID, 7L);
    }

    @Test
    void crossOwnerGetIsNotFound() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(queries.find(USER_ID, 7L)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/7").with(user("another-user").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(queries).find(USER_ID, 7L);
    }

    @Test
    void transitionMapsTypedCommandVersionKeyAndReason() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 7L, "PROPOSED", 0L, "propose-1", "ready"))
                .thenReturn(experiment(7L, USER_ID, ExperimentStatus.PROPOSED, 1L));

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"PROPOSED\",\"expectedVersion\":0,"
                                + "\"idempotencyKey\":\"propose-1\",\"reason\":\"ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.aggregateVersion").value(1));

        verify(commands).transition(USER_ID, 7L, "PROPOSED", 0L, "propose-1", "ready");
    }

    @Test
    void duplicateTransitionIsSafeToReplay() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 7L, "PROPOSED", 0L, "propose-once", null))
                .thenReturn(experiment(7L, USER_ID, ExperimentStatus.PROPOSED, 1L));
        String body = "{\"command\":\"PROPOSED\",\"expectedVersion\":0,\"idempotencyKey\":\"propose-once\"}";

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(commands, times(2)).transition(USER_ID, 7L, "PROPOSED", 0L, "propose-once", null);
    }

    @Test
    void stableConflictCodesDoNotLeakProviderOrDatabaseText() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        String body = "{\"command\":\"ACCEPTED\",\"expectedVersion\":0,\"idempotencyKey\":\"key\"}";

        when(commands.transition(USER_ID, 7L, "ACCEPTED", 0L, "key", null))
                .thenThrow(new AggregateVersionConflictException())
                .thenThrow(new InvalidTransitionException("database details must stay private"))
                .thenThrow(new IdempotencyConflictException())
                .thenThrow(new ExperimentInFlightConflictException());
        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Invalid state transition"));

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(post(BASE + "/7/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPERIMENT_IN_FLIGHT_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Another Experiment is already in flight"));
    }

    @Test
    void invalidBoundsReturnValidationErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content(validBody()
                                .replace("\"hypothesis\":\"Sleep improves\"", "\"hypothesis\":\"\"")
                                 .replace("\"durationDays\":14", "\"durationDays\":91")
                                .replace("\"meaningfulChange\":1", "\"meaningfulChange\":0")
                                .replace("\"stopConditions\":[{\"code\":\"PAIN\",\"description\":\"Stop on pain\"}]",
                                        "\"stopConditions\":[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(commands, never()).create(any(), any(Experiment.class), any());
    }

    @Test
    void allRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(post(BASE).contentType("application/json").content(validBody()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE + "/7")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post(BASE + "/7/transitions").contentType("application/json")
                        .content("{\"command\":\"PROPOSED\",\"expectedVersion\":0,\"idempotencyKey\":\"key\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static String validBody() {
        return """
                {"userId":999,"investigationId":11,"goalId":12,
                 "hypothesis":"Sleep improves",
                 "baselineStartDate":"2026-08-01","baselineEndDate":"2026-08-07",
                 "durationDays":14,
                 "intervention":{"action":"No late meals","protocol":"Stop eating after 20:00"},
                 "primaryMetric":"sleep_duration","secondaryMetrics":["energy"],
                 "stopConditions":[{"code":"PAIN","description":"Stop on pain"}],
                 "outcomeDirection":"INCREASE","meaningfulChange":1}
                """;
    }

    private static Experiment experiment(Long id, Long userId, ExperimentStatus status, long version) {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        Instant acceptedAt = null;
        Instant startedAt = null;
        Instant rejectedAt = null;
        Instant abortedAt = null;
        Instant completedAt = null;
        Instant evaluatedAt = null;
        if (status == ExperimentStatus.ACCEPTED || status == ExperimentStatus.ACTIVE
                || status == ExperimentStatus.PAUSED || status == ExperimentStatus.COMPLETED
                || status == ExperimentStatus.EVALUATED) {
            acceptedAt = now;
        }
        if (status == ExperimentStatus.ACTIVE || status == ExperimentStatus.PAUSED
                || status == ExperimentStatus.COMPLETED || status == ExperimentStatus.EVALUATED) {
            startedAt = now;
        }
        if (status == ExperimentStatus.REJECTED) {
            rejectedAt = now;
        }
        if (status == ExperimentStatus.ABORTED) {
            abortedAt = now;
        }
        if (status == ExperimentStatus.COMPLETED || status == ExperimentStatus.EVALUATED) {
            completedAt = now;
        }
        if (status == ExperimentStatus.EVALUATED) {
            evaluatedAt = now;
        }
        return new Experiment(id, userId, 11L, 12L,
                new Hypothesis("Sleep improves"), LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-07"), 14,
                new Intervention("No late meals", "Stop eating after 20:00"), "sleep_duration",
                List.of("energy"), List.of(new StopCondition("PAIN", "Stop on pain")),
                OutcomeDirection.INCREASE, new BigDecimal("0.5"), status, version,
                now, acceptedAt, startedAt, rejectedAt, abortedAt, completedAt, evaluatedAt, now);
    }
}
