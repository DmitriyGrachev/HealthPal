package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.port.in.UserDataExportManifestUseCase;
import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.ai.RateLimiterService;
import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.auth.domain.UserDataExportManifest;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import com.fit.fitnessapp.auth.domain.UserDataModuleExport;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserDataExportV2Controller.class, UserDataLifecycleController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, TokenFilter.class, JwtCore.class, ApiErrorResponseWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class UserDataExportV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserDataExportManifestUseCase manifestUseCase;

    @MockitoBean
    private UserDataLifecycleUseCase lifecycleUseCase;

    @MockitoBean
    private CurrentUserApi currentUserApi;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    void unauthenticatedRequestIsUnauthorizedAndDoesNotInvokeManifestUseCase() throws Exception {
        mockMvc.perform(get("/api/v2/user/me/export"))
                .andExpect(status().isUnauthorized());

        verify(manifestUseCase, never()).exportUserData(org.mockito.ArgumentMatchers.anyLong());
        verify(currentUserApi, never()).getCurrentUserId();
    }

    @Test
    void authenticatedUserExportsOnlyCurrentIdentityAndIgnoresQueryUserId() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(manifestUseCase.exportUserData(42L)).thenReturn(new UserDataExportManifest(
                2,
                42L,
                "me@example.test",
                "me",
                Instant.parse("2026-08-19T10:15:30Z"),
                List.of(new UserDataModuleExport("auth", 1, List.of(disclosure()), Map.of("notes", List.of())))));

        var response = mockMvc.perform(get("/api/v2/user/me/export")
                        .param("userId", "999")
                        .with(user("me").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion").value(2))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("me@example.test"))
                .andExpect(jsonPath("$.username").value("me"))
                .andExpect(jsonPath("$.exportedAt").value("2026-08-19T10:15:30Z"))
                .andExpect(jsonPath("$.modules[0].moduleKey").value("auth"))
                .andExpect(jsonPath("$.modules[0].data.notes").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder(
                "manifestVersion", "userId", "email", "username", "exportedAt", "modules");

        verify(currentUserApi).getCurrentUserId();
        verify(manifestUseCase).exportUserData(eq(42L));
    }

    @Test
    void v1RouteRetainsFlatExportShapeWithoutManifestFields() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(lifecycleUseCase.exportUserData(42L)).thenReturn(new UserDataExportDto(
                42L,
                "me@example.test",
                "me",
                Instant.parse("2026-08-19T10:15:30Z"),
                Map.of("goal_weight_kg", 75.0),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of()));

        mockMvc.perform(get("/api/v1/user/me/export").with(user("me").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.profile.goal_weight_kg").value(75.0))
                .andExpect(jsonPath("$.fatSecretConnected").value(false))
                .andExpect(jsonPath("$.manifestVersion").doesNotExist())
                .andExpect(jsonPath("$.modules").doesNotExist());

        verify(currentUserApi).getCurrentUserId();
        verify(lifecycleUseCase).exportUserData(42L);
    }

    private static DataRetentionDisclosure disclosure() {
        return new DataRetentionDisclosure(
                "identity",
                DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                null,
                List.of(),
                DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION);
    }
}
