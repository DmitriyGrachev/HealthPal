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
import com.fit.fitnessapp.experiment.application.port.in.GoalCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.GoalQueryUseCase;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.InvalidTransitionException;
import com.fit.fitnessapp.experiment.domain.PrimaryGoalConflictException;
import com.fit.fitnessapp.experiment.domain.TargetRange;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(GoalController.class)
@AutoConfigureMockMvc
@Import({ExperimentExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class, TokenFilter.class, JwtCore.class,
        ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class GoalControllerTest {

    private static final Long USER_ID = 42L;
    private static final String BASE = "/api/v1/goals";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GoalCommandUseCase commands;
    @MockitoBean private GoalQueryUseCase queries;
    @MockitoBean private CurrentUserApi currentUserApi;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private RateLimiterService rateLimiterService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void createDerivesOwnerAndMapsBoundedGoalFields() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Goal created = goal(8L, USER_ID, GoalStatus.DRAFT, 0L, false);
        when(commands.create(eq(USER_ID), any(Goal.class), eq("goal-create"))).thenReturn(created);

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "goal-create")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId":999,"type":"WEIGHT_LOSS","name":"Lose weight",
                                  "metric":"WEIGHT","targetRange":{"minimum":70,"maximum":75,"unit":"kg"},
                                  "priority":10,"primary":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.userId").value(USER_ID));

        var captor = org.mockito.ArgumentCaptor.forClass(Goal.class);
        verify(commands).create(eq(USER_ID), captor.capture(), eq("goal-create"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().priority()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().primary()).isTrue();
    }

    @Test
    void duplicateCreateReturnsSameResourceAndLocation() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Goal created = goal(8L, USER_ID, GoalStatus.DRAFT, 0L, false);
        when(commands.create(eq(USER_ID), any(Goal.class), eq("goal-once"))).thenReturn(created);
        String body = "{\"type\":\"WEIGHT_LOSS\",\"name\":\"Lose weight\",\"metric\":\"WEIGHT\",\"idempotencyKey\":\"goal-once\"}";

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Location"))
                        .isEqualTo("/api/v1/goals/8"));
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Location"))
                        .isEqualTo("/api/v1/goals/8"));

        verify(commands, org.mockito.Mockito.times(2))
                .create(eq(USER_ID), any(Goal.class), eq("goal-once"));
    }

    @Test
    void createRequiresOneKeyAndRejectsConflictingHeaderAndBody() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"type\":\"WEIGHT_LOSS\",\"name\":\"Lose weight\",\"metric\":\"WEIGHT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .header("Idempotency-Key", "header-key")
                        .contentType("application/json")
                        .content("{\"type\":\"WEIGHT_LOSS\",\"name\":\"Lose weight\",\"metric\":\"WEIGHT\",\"idempotencyKey\":\"body-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void listIsCurrentUserScoped() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(queries.findAll(USER_ID)).thenReturn(List.of(goal(8L, USER_ID, GoalStatus.DRAFT, 0L, false)));

        mockMvc.perform(get(BASE).with(user("me").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8));
        verify(queries).findAll(USER_ID);
    }

    @Test
    void crossOwnerGetHasStableNotFound() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(queries.find(USER_ID, 8L)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/8").with(user("me").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(queries).find(USER_ID, 8L);
        verify(currentUserApi).getCurrentUserId();
    }

    @Test
    void crossOwnerTransitionHasTheSameStableNotFound() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "missing", null))
                .thenThrow(new ExperimentNotFoundException());

        mockMvc.perform(post(BASE + "/8/transitions").with(user("another-user").roles("USER"))
                        .contentType("application/json")
                        .content("{\"userId\":999,\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));

        verify(commands).transition(USER_ID, 8L, "ACTIVE", 0L, "missing", null);
        verify(currentUserApi).getCurrentUserId();
    }

    @Test
    void transitionPassesTypedCommandVersionKeyAndReason() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Goal transitioned = goal(8L, USER_ID, GoalStatus.ACTIVE, 1L, true);
        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "activate-1", "start")).thenReturn(transitioned);

        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("""
                                {"command":"ACTIVE","expectedVersion":0,"idempotencyKey":"activate-1","reason":"start"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.aggregateVersion").value(1));
        verify(commands).transition(USER_ID, 8L, "ACTIVE", 0L, "activate-1", "start");
    }

    @Test
    void duplicateTransitionCommandIsSafeToReplay() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Goal result = goal(8L, USER_ID, GoalStatus.ACTIVE, 1L, true);
        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "activate-once", null)).thenReturn(result);
        String body = "{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"activate-once\"}";

        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        verify(commands, org.mockito.Mockito.times(2))
                .transition(USER_ID, 8L, "ACTIVE", 0L, "activate-once", null);
    }

    @Test
    void versionAndPrimaryConflictsUseStableCodes() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "stale", null))
                .thenThrow(new AggregateVersionConflictException());
        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "primary", null))
                .thenThrow(new PrimaryGoalConflictException());
        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"primary\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIMARY_GOAL_CONFLICT"));
    }

    @Test
    void illegalTransitionNotFoundAndIdempotencyMismatchUseStableCodes() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "illegal", null))
                .thenThrow(new InvalidTransitionException("not legal"));
        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"illegal\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "missing", null))
                .thenThrow(new ExperimentNotFoundException());
        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        when(commands.transition(USER_ID, 8L, "ACTIVE", 0L, "same-key", null))
                .thenThrow(new IdempotencyConflictException());
        mockMvc.perform(post(BASE + "/8/transitions").with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"same-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void invalidBoundsUseValidationCodeAndDoNotCallUseCase() throws Exception {
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"type\":\"WEIGHT_LOSS\",\"name\":\"x\",\"metric\":\"WEIGHT\",\"priority\":1001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(BASE).with(user("me").roles("USER"))
                        .contentType("application/json")
                        .content("{\"type\":\"WEIGHT_LOSS\",\"name\":\"x\",\"metric\":\"WEIGHT\",\"targetRange\":{\"minimum\":80,\"maximum\":70,\"unit\":\"kg\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(commands, never()).create(eq(USER_ID), any(Goal.class), any());
    }

    @Test
    void allRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(post(BASE).contentType("application/json")
                        .content("{\"type\":\"WEIGHT_LOSS\",\"name\":\"x\",\"metric\":\"WEIGHT\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(BASE + "/8")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post(BASE + "/8/transitions").contentType("application/json")
                        .content("{\"command\":\"ACTIVE\",\"expectedVersion\":0,\"idempotencyKey\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static Goal goal(Long id, Long userId, GoalStatus status, long version, boolean primary) {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        return new Goal(id, userId, GoalType.WEIGHT_LOSS, "Lose weight", GoalMetric.WEIGHT,
                new TargetRange(70d, 75d, "kg"), status, LocalDate.parse("2026-12-31"), 10,
                GoalSource.USER, null, null, primary, version, now, null, now);
    }
}
