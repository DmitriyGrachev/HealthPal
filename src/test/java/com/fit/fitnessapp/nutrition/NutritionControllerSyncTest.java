package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.nutrition.adapter.in.web.NutritionController;
import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NutritionController.class)
@AutoConfigureMockMvc(addFilters = false)
class NutritionControllerSyncTest {

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
    void syncTodayReturnsOkStatusDtoAndRunsSyncForCurrentUser() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);
        when(userTimeApi.currentDate(42L)).thenReturn(LocalDate.of(2026, 7, 1));

        mockMvc.perform(post("/api/v1/nutrition/sync/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.scope").value("today"));

        verify(syncUseCase, times(1)).syncDay(eq(42L), any(LocalDate.class));
    }

    @Test
    void syncCurrentMonthReturnsOkStatusDtoAndRunsSyncForCurrentUser() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);

        mockMvc.perform(post("/api/v1/nutrition/sync/current-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.scope").value("current-month"));

        verify(syncUseCase, times(1)).syncMonth(42L);
    }
}
