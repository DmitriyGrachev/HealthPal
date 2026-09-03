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
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCheckInUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentDecisionUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentEvaluationUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentOutcomeUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ContextRating;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.CheckInDateConflictException;
import com.fit.fitnessapp.experiment.domain.ConfounderAssessment;
import com.fit.fitnessapp.experiment.domain.DataQuality;
import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ObservedEffect;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import com.fit.fitnessapp.experiment.domain.UserDecision;
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
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExperimentEvidenceController.class)
@AutoConfigureMockMvc
@Import({ExperimentExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class, TokenFilter.class,
        JwtCore.class, ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class ExperimentEvidenceControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExperimentCheckInUseCase checkIns;
    @MockitoBean private ExperimentOutcomeUseCase outcomes;
    @MockitoBean private ExperimentEvaluationUseCase evaluations;
    @MockitoBean private ExperimentDecisionUseCase decisions;
    @MockitoBean private CurrentUserApi currentUserApi;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private RateLimiterService rateLimiterService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void checkInDerivesOwnerAndExperimentAndReturnsStableResponse() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        ExperimentCheckIn stored = new ExperimentCheckIn(9L, USER_ID, 7L,
                LocalDate.parse("2026-08-22"), ZoneId.of("Europe/Chisinau"), null, null,
                AdherenceStatus.YES, null, null, "felt good", new ContextRating(8), null, null,
                CheckInSource.MANUAL, now, now);
        when(checkIns.recordWithStatus(eq(USER_ID), eq(7L), any(ExperimentCheckIn.class), eq("check-in-1")))
                .thenReturn(new EvidenceCommandResult<>(stored, true), new EvidenceCommandResult<>(stored, false));

        mockMvc.perform(post("/api/v1/experiments/7/check-ins")
                        .with(user("me").roles("USER"))
                        .header("Idempotency-Key", "check-in-1")
                        .contentType("application/json")
                        .content("""
                                {"localDate":"2026-08-22","timezone":"Europe/Chisinau",
                                 "adherence":"YES","note":"felt good","readiness":8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.experimentId").value(7))
                .andExpect(jsonPath("$.timezone").value("Europe/Chisinau"))
                .andExpect(jsonPath("$.readiness").value(8));

        mockMvc.perform(post("/api/v1/experiments/7/check-ins")
                        .with(user("me").roles("USER"))
                        .header("Idempotency-Key", "check-in-1")
                        .contentType("application/json")
                        .content("""
                                {"localDate":"2026-08-22","timezone":"Europe/Chisinau",
                                 "adherence":"YES","note":"felt good","readiness":8}
                                """))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ExperimentCheckIn.class);
        verify(checkIns, org.mockito.Mockito.times(2)).recordWithStatus(eq(USER_ID), eq(7L),
                captor.capture(), eq("check-in-1"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().experimentId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().source()).isEqualTo(CheckInSource.MANUAL);
    }

    @Test
    void checkInRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/experiments/7/check-ins")
                        .contentType("application/json")
                        .content("{\"localDate\":\"2026-08-22\",\"timezone\":\"UTC\",\"adherence\":\"YES\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/experiments/7/outcomes")
                        .contentType("application/json")
                        .content("{\"metricKey\":\"weight\",\"baselineValue\":1,"
                                + "\"observedValue\":2,\"unit\":\"kg\",\"baselineSampleCount\":2,"
                                + "\"observedSampleCount\":2,\"observedAt\":\"2026-08-22T12:00:00Z\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/experiments/7/evaluation")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/experiments/7/decision")
                        .contentType("application/json")
                        .content("{\"evaluationId\":9,\"decision\":\"KEEP\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(checkIns, never()).recordWithStatus(any(), any(), any(), any());
    }

    @Test
    void outcomeEvaluationAndDecisionBindOwnerAndExperimentWithContractStatuses() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        Outcome outcome = new Outcome(10L, USER_ID, 7L, "weight", BigDecimal.TEN,
                new BigDecimal("9.5"), "kg", 2, 2, now, OutcomeSource.MANUAL, null, now);
        Evaluation evaluation = new Evaluation(11L, USER_ID, 7L, "V1", EvaluationDecision.MODIFY,
                DataQuality.SUFFICIENT, ObservedEffect.NEUTRAL, ConfounderAssessment.NONE,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 0,
                Map.<String, Object>of("expectedVersion", 2), List.of("NO_MEANINGFUL_EFFECT"), now);
        UserDecision decision = new UserDecision(12L, USER_ID, 7L, 11L,
                EvaluationDecision.MODIFY, "confirm", now);
        when(outcomes.recordWithStatus(eq(USER_ID), eq(7L), any(Outcome.class), eq("outcome-1")))
                .thenReturn(new EvidenceCommandResult<>(outcome, true));
        when(evaluations.evaluateWithStatus(eq(USER_ID), eq(7L), eq(2L), eq("evaluation-1")))
                .thenReturn(new EvidenceCommandResult<>(evaluation, false));
        when(decisions.decideWithStatus(eq(USER_ID), eq(7L), eq(11L), eq(EvaluationDecision.MODIFY),
                eq("confirm"), eq(List.of()), eq("decision-1")))
                .thenReturn(new EvidenceCommandResult<>(decision, true));

        mockMvc.perform(post("/api/v1/experiments/7/outcomes").with(user("me").roles("USER"))
                        .header("Idempotency-Key", "outcome-1").contentType("application/json")
                        .content("""
                                {"metricKey":"weight","baselineValue":10,"observedValue":9.5,
                                 "unit":"kg","baselineSampleCount":2,"observedSampleCount":2,
                                 "observedAt":"2026-08-23T12:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.experimentId").value(7));
        mockMvc.perform(post("/api/v1/experiments/7/evaluation").with(user("me").roles("USER"))
                        .header("Idempotency-Key", "evaluation-1").contentType("application/json")
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.experimentId").value(7));
        mockMvc.perform(post("/api/v1/experiments/7/decision").with(user("me").roles("USER"))
                        .header("Idempotency-Key", "decision-1").contentType("application/json")
                        .content("{\"evaluationId\":11,\"decision\":\"MODIFY\",\"note\":\"confirm\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.experimentId").value(7))
                .andExpect(jsonPath("$.evaluationId").value(11));

        var outcomeCaptor = org.mockito.ArgumentCaptor.forClass(Outcome.class);
        verify(outcomes).recordWithStatus(eq(USER_ID), eq(7L), outcomeCaptor.capture(), eq("outcome-1"));
        org.assertj.core.api.Assertions.assertThat(outcomeCaptor.getValue().userId()).isEqualTo(USER_ID);
        org.assertj.core.api.Assertions.assertThat(outcomeCaptor.getValue().experimentId()).isEqualTo(7L);
        verify(evaluations).evaluateWithStatus(eq(USER_ID), eq(7L), eq(2L), eq("evaluation-1"));
        verify(decisions).decideWithStatus(eq(USER_ID), eq(7L), eq(11L), eq(EvaluationDecision.MODIFY),
                eq("confirm"), eq(List.of()), eq("decision-1"));

        var reference = new com.fit.fitnessapp.experiment.api.DecisionClaimReference(101L, 1, "a".repeat(64));
        when(decisions.decideWithStatus(USER_ID, 7L, 11L, EvaluationDecision.MODIFY,
                null, List.of(reference), "disputed-context"))
                .thenThrow(new com.fit.fitnessapp.experiment.api.DecisionContextRejectedException());
        mockMvc.perform(post("/api/v1/experiments/7/decision").with(user("me").roles("USER"))
                        .header("Idempotency-Key", "disputed-context").contentType("application/json")
                        .content("""
                                {"evaluationId":11,"decision":"MODIFY",
                                 "claimsUsed":[{"claimId":101,"version":1,"contentHash":"%s"}]}
                                """.formatted(reference.contentHash())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECISION_CONTEXT_REJECTED"));
    }

    @Test
    void missingExperimentMapsToNotFoundWithoutLeakingOwnership() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        when(outcomes.recordWithStatus(eq(USER_ID), eq(7L), any(Outcome.class), eq("missing-1")))
                .thenThrow(new ExperimentNotFoundException());

        mockMvc.perform(post("/api/v1/experiments/7/outcomes").with(user("me").roles("USER"))
                        .header("Idempotency-Key", "missing-1").contentType("application/json")
                        .content("""
                                {"metricKey":"weight","baselineValue":10,"observedValue":9.5,
                                 "unit":"kg","baselineSampleCount":2,"observedSampleCount":2,
                                 "observedAt":"2026-08-23T12:00:00Z"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void duplicateDateConflictUsesStableCode() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        doThrow(new CheckInDateConflictException()).when(checkIns)
                .recordWithStatus(eq(USER_ID), eq(7L), any(ExperimentCheckIn.class), eq("date-key"));

        mockMvc.perform(post("/api/v1/experiments/7/check-ins")
                        .with(user("me").roles("USER"))
                        .header("Idempotency-Key", "date-key")
                        .contentType("application/json")
                        .content("{\"localDate\":\"2026-08-22\",\"timezone\":\"UTC\",\"adherence\":\"NO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_IN_DATE_CONFLICT"));
    }

    @Test
    void conflictingHeaderAndBodyKeysAreRejectedBeforeDelegation() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);

        mockMvc.perform(post("/api/v1/experiments/7/check-ins")
                        .with(user("me").roles("USER"))
                        .header("Idempotency-Key", "header-key")
                        .contentType("application/json")
                        .content("{\"localDate\":\"2026-08-22\",\"timezone\":\"UTC\","
                                + "\"adherence\":\"YES\",\"idempotencyKey\":\"body-key\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        verify(checkIns, never()).recordWithStatus(any(), any(), any(), any());
    }
}
