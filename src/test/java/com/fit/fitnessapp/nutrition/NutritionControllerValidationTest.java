package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.nutrition.adapter.in.web.NutritionController;
import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NutritionController.class)
@AutoConfigureMockMvc(addFilters = false)
class NutritionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectFatSecretUseCase connectUseCase;
    @MockitoBean
    private SyncNutritionUseCase syncUseCase;
    @MockitoBean
    private NutritionQueryUseCase queryUseCase;
    @MockitoBean
    private CurrentUserApi currentUserApi;
    @MockitoBean
    private UserTimeApi userTimeApi;
    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean
    private TokenFilter tokenFilter;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void dateRangeRejectsFromAfterToBeforeCallingUseCase() throws Exception {
        mockMvc.perform(get("/api/v1/nutrition/range")
                        .param("from", "2026-07-10")
                        .param("to", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/nutrition/range"));

        verify(queryUseCase, never()).getDateRange(any(), any(), any());
    }

    @Test
    void dateRangeCallsUseCaseForValidRange() throws Exception {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 10);
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(queryUseCase.getDateRange(42L, from, to)).thenReturn(List.of(
                new NutritionDaySummary(42L, from, 20260701, 1800.0, 120.0, 60.0, 180.0)));

        mockMvc.perform(get("/api/v1/nutrition/range")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(42))
                .andExpect(jsonPath("$[0].date").value("2026-07-01"));

        verify(queryUseCase).getDateRange(eq(42L), eq(from), eq(to));
    }
}
