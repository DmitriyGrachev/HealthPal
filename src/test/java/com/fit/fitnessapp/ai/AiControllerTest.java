package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerTest {

    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserApi currentUserApi;

    @MockitoBean
    private UserTimeApi userTimeApi;

    @MockitoBean
    private AiInsightRepository insightRepository;

    @MockitoBean
    private FitnessAiService fitnessAiService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private JwtCore jwtCore;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ApiErrorResponseWriter errorResponseWriter;

    @Test
    void todayInsightReturnsTypedPendingResponseWhenMissing() throws Exception {
        allowCurrentUser();
        when(userTimeApi.currentDate(USER_ID)).thenReturn(LocalDate.of(2026, 7, 1));
        when(insightRepository.findByUserIdAndDateAndInsightType(
                eq(USER_ID), any(LocalDate.class), eq(InsightType.DAILY)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/ai/insights/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.message").value("Daily insight has not been generated yet"));
    }

    @Test
    void todayInsightReturnsTypedInsightResponseWhenPresent() throws Exception {
        LocalDate today = LocalDate.now();
        allowCurrentUser();
        when(userTimeApi.currentDate(USER_ID)).thenReturn(today);
        when(insightRepository.findByUserIdAndDateAndInsightType(USER_ID, today, InsightType.DAILY))
                .thenReturn(Optional.of(AiInsightEntity.builder()
                        .userId(USER_ID)
                        .date(today)
                        .insightType(InsightType.DAILY)
                        .insightText("Stay consistent today")
                        .structuredResponse(structured(today))
                        .build()));

        mockMvc.perform(get("/api/v1/ai/insights/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.type").value("DAILY"))
                .andExpect(jsonPath("$.summary").value("Stay consistent today"))
                .andExpect(jsonPath("$.structured.reportType").value("DAILY"))
                .andExpect(jsonPath("$.structured.summary").value("Structured nutrition summary"));
    }

    @Test
    void generateInsightReturnsTypedAcceptedResponse() throws Exception {
        LocalDate targetDate = LocalDate.of(2026, 3, 16);
        allowCurrentUser();
        when(fitnessAiService.generateDailyInsight(USER_ID, targetDate))
                .thenReturn(DailyInsightResult.generated());

        mockMvc.perform(post("/api/v1/ai/insights/generate")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.message").value("Daily insight generated"))
                .andExpect(jsonPath("$.resultStatus").value("GENERATED"))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.date").value(targetDate.toString()));

        verify(fitnessAiService).generateDailyInsight(USER_ID, targetDate);
    }

    @Test
    void generateInsightReturnsNoSnapshotStatusWhenNoDailySourceExists() throws Exception {
        LocalDate targetDate = LocalDate.of(2026, 3, 16);
        allowCurrentUser();
        when(fitnessAiService.generateDailyInsight(USER_ID, targetDate))
                .thenReturn(DailyInsightResult.noSnapshot());

        mockMvc.perform(post("/api/v1/ai/insights/generate")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("no_snapshot"))
                .andExpect(jsonPath("$.message").value("No nutrition or workout data is available for this date"))
                .andExpect(jsonPath("$.resultStatus").value("NO_SNAPSHOT"))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.date").value(targetDate.toString()));
    }

    private void allowCurrentUser() {
        when(currentUserApi.getCurrentUserId()).thenReturn(USER_ID);
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(true);
        when(rateLimiterService.resolveBucket(USER_ID)).thenReturn(bucket);
    }

    private NutritionInsightResponse structured(LocalDate date) {
        return new NutritionInsightResponse(
                NutritionInsightResponse.ReportType.DAILY,
                new NutritionInsightResponse.Period(date, date),
                "Structured nutrition summary",
                "Telegram summary",
                new NutritionInsightResponse.MacroAnalysis(
                        2_000.0,
                        150.0,
                        70.0,
                        220.0,
                        NutritionInsightResponse.CalorieBalance.MAINTENANCE,
                        NutritionInsightResponse.ProteinAdequacy.ADEQUATE),
                NutritionInsightResponse.WeightTrend.STALLING,
                List.of(),
                List.of(),
                List.of("How was your energy today?"),
                0.8f,
                0.9f);
    }
}
