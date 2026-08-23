package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.ai.RateLimiterService;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.workout.adapter.in.web.WorkoutAnalyticController;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkoutAnalyticController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkoutAnalyticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkoutQueryUseCase workoutQueryUseCase;

    @MockitoBean
    private CurrentUserApi currentUserApi;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private JwtCore jwtCore;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ApiErrorResponseWriter errorResponseWriter;

    @Test
    void canonicalWorkoutAnalyticSummaryPathWorks() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        Bucket bucket = allowingBucket();
        when(rateLimiterService.resolveBucket(7L)).thenReturn(bucket);
        when(workoutQueryUseCase.getAllWorkoutSummaryThisWeek(7L))
                .thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/v1/workout-analytic/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$[0].totalReps").value(6))
                .andExpect(jsonPath("$[0].totalWeight").value(130.0));

        verify(workoutQueryUseCase).getAllWorkoutSummaryThisWeek(7L);
    }

    @Test
    void legacyWorkoutAnaliticSummaryPathStillWorks() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        Bucket bucket = allowingBucket();
        when(rateLimiterService.resolveBucket(7L)).thenReturn(bucket);
        when(workoutQueryUseCase.getAllWorkoutSummaryThisWeek(7L))
                .thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/v1/workout-analitic/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exerciseName").value("Bench Press"));

        verify(workoutQueryUseCase).getAllWorkoutSummaryThisWeek(7L);
    }

    private WorkoutSummaryWeeklyDto summary() {
        return WorkoutSummaryWeeklyDto.builder()
                .exerciseName("Bench Press")
                .totalReps(6L)
                .totalWeight(130.0)
                .date(LocalDateTime.of(2026, 3, 16, 0, 0))
                .build();
    }

    private Bucket allowingBucket() {
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(true);
        return bucket;
    }
}
