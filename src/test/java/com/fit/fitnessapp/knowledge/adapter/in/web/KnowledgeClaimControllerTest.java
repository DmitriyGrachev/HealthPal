package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.ai.RateLimiterService;
import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimQueryUseCase;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimIdempotencyConflictException;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimVersionConflictException;
import com.fit.fitnessapp.knowledge.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({KnowledgeClaimController.class, ClaimConflictController.class})
@AutoConfigureMockMvc
@Import({KnowledgeExceptionHandler.class, SecurityConfig.class, TokenFilter.class, JwtCore.class,
        ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class KnowledgeClaimControllerTest {
    private static final String BASE = "/api/v1/knowledge/claims";
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final String COMMAND = "{\"expectedVersion\":0,\"idempotencyKey\":\"action-1\"}";
    @Autowired MockMvc mvc;
    @MockitoBean KnowledgeClaimInspectorUseCase commands;
    @MockitoBean KnowledgeClaimQueryUseCase queries;
    @MockitoBean ClaimConflictQueryUseCase conflicts;
    @MockitoBean CurrentUserApi currentUser;
    @MockitoBean RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean RateLimiterService rateLimiterService;
    @MockitoBean UserDetailsService userDetailsService;

    @BeforeEach
    void owner() { when(currentUser.getCurrentUserId()).thenReturn(42L); }

    @Test
    void readsOwnedClaimsWithProvenanceHistoryAndOpenConflicts() throws Exception {
        KnowledgeClaim claim = claim();
        when(queries.findAll(42L)).thenReturn(List.of(claim));
        when(queries.find(42L, 7L)).thenReturn(Optional.of(claim));
        when(queries.findHistory(42L, 7L)).thenReturn(List.of(claim));
        when(conflicts.findOpen(42L)).thenReturn(List.of(
                new ClaimConflict(1L, 7L, 8L, "VALUE_CONTRADICTION", "OPEN", NOW)));

        mvc.perform(get(BASE).param("userId", "999").with(user("me")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(7));
        mvc.perform(get(BASE + "/7").with(user("me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.source.sourceType").value("USER_NOTE"))
                .andExpect(jsonPath("$.claim.evidence[0].evidenceId").value("note-1"))
                .andExpect(jsonPath("$.claim.origin").value("USER_DECLARED"))
                .andExpect(jsonPath("$.claim.verification").value("PROPOSED"))
                .andExpect(jsonPath("$.claim.temporalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.claim.observedAt").exists())
                .andExpect(jsonPath("$.claim.validFrom").exists())
                .andExpect(jsonPath("$.claim.validUntil").exists())
                .andExpect(jsonPath("$.history[0].id").value(7));
        mvc.perform(get("/api/v1/knowledge/conflicts").with(user("me")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].leftClaimId").value(7));
        verify(queries).findAll(42L);
        verify(conflicts).findOpen(42L);
    }

    @Test
    void mutationRoutesPassCurrentOwnerVersionAndKey() throws Exception {
        when(commands.confirm(42L, 7L, 0, "action-1")).thenReturn(claim());
        when(commands.dispute(42L, 7L, 0, "action-1")).thenReturn(claim());
        for (String action : List.of("confirm", "dispute")) {
            mvc.perform(post(BASE + "/7/" + action).with(user("me"))
                            .contentType("application/json").content(COMMAND))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        }
        mvc.perform(delete(BASE + "/7").with(user("me"))
                        .param("expectedVersion", "0").header("Idempotency-Key", "action-1"))
                .andExpect(status().isNoContent());
        verify(commands).confirm(42L, 7L, 0, "action-1");
        verify(commands).dispute(42L, 7L, 0, "action-1");
        verify(commands).forget(42L, 7L, 0, "action-1");
    }

    @Test
    void correctionAcceptsOnlyContentAndDoesNotLetCallerChooseOwnerOrTrust() throws Exception {
        when(commands.correctByUser(eq(42L), eq(7L), any(), any(), any(), eq(NOW), isNull(), isNull(),
                eq(0L), eq("edit-1"))).thenReturn(claim());
        mvc.perform(put(BASE + "/7").with(user("me")).contentType("application/json").content("""
                {"userId":999,"subject":"self","predicate":"preference",
                 "value":{"type":"TEXT","canonicalValue":"morning"},
                 "observedAt":"2026-09-03T12:00:00Z","expectedVersion":0,"idempotencyKey":"edit-1",
                 "origin":"IMPORTED","verification":"SUPPORTED","evidence":[]}
                """))
                .andExpect(status().isOk());
        verify(commands).correctByUser(42L, 7L, new ClaimSubject("self"), new ClaimPredicate("preference"),
                TypedClaimValue.text("morning"), NOW, null, null, 0, "edit-1");
    }

    @Test
    void missingForeignClaimAndConflictingCommandsUseStableErrors() throws Exception {
        when(queries.find(42L, 7L)).thenReturn(Optional.empty());
        mvc.perform(get(BASE + "/7").with(user("me"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        when(commands.confirm(42L, 7L, 0, "action-1")).thenThrow(new KnowledgeClaimVersionConflictException());
        mvc.perform(post(BASE + "/7/confirm").with(user("me")).contentType("application/json").content(COMMAND))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        when(commands.dispute(42L, 7L, 0, "action-1")).thenThrow(new KnowledgeClaimIdempotencyConflictException());
        mvc.perform(post(BASE + "/7/dispute").with(user("me")).contentType("application/json").content(COMMAND))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void malformedCommandsAreRejectedBeforeMutation() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                post(BASE + "/7/confirm").content("{\"idempotencyKey\":\"x\"}"),
                post(BASE + "/7/dispute").content("{\"expectedVersion\":-1,\"idempotencyKey\":\"x\"}"),
                put(BASE + "/7").content("{}"),
                put(BASE + "/7").content("""
                        {"subject":"self","predicate":"weight","value":{"type":"DECIMAL","canonicalValue":"1e999999999"},
                         "observedAt":"2026-09-03T12:00:00Z","expectedVersion":0,"idempotencyKey":"x"}
                        """),
                delete(BASE + "/7").param("expectedVersion", "0").header("Idempotency-Key", " "),
                get(BASE + "/0"))) {
            mvc.perform(request.with(user("me")).contentType("application/json"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(commands);
    }

    @Test
    void allInspectorRoutesRequireAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(get(BASE), get(BASE + "/7"),
                post(BASE + "/7/confirm"), post(BASE + "/7/dispute"), put(BASE + "/7"),
                delete(BASE + "/7"), get("/api/v1/knowledge/conflicts"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(commands, queries, conflicts);
    }

    private static KnowledgeClaim claim() {
        return KnowledgeClaim.create(42L, new ClaimSubject("self"), new ClaimPredicate("preference"),
                TypedClaimValue.text("morning"), ClaimOrigin.USER_DECLARED, ClaimVerification.PROPOSED,
                new ClaimSourceRef("USER_NOTE", "note-1", 1), NOW, NOW, NOW.plusSeconds(3600),
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "1"),
                List.of(new ClaimEvidence("USER_NOTE", "note-1", 1, "a".repeat(64), NOW)), NOW).withId(7L);
    }
}
