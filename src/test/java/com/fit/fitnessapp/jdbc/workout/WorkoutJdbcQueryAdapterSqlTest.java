package com.fit.fitnessapp.jdbc.workout;

import com.fit.fitnessapp.workout.WorkoutDailyStatsDto;
import com.fit.fitnessapp.workout.adapter.out.WorkoutJdbcQueryAdapter;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkoutJdbcQueryAdapterSqlTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final WorkoutJdbcQueryAdapter adapter = new WorkoutJdbcQueryAdapter(jdbc);

    @Test
    void workoutQueriesUseMigratedWorkoutTableName() {
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.query(anyString(), any(Map.class), any(ResultSetExtractor.class)))
                .thenReturn(Map.of());

        adapter.getAllWorkoutSummaryByUser(1L);
        adapter.getAllWorkoutSummaryThisWeek(1L);
        adapter.getDailyStats(1L, LocalDate.of(2026, 1, 1));
        adapter.getWeeklyStats(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7));
        adapter.getMonthlyStats(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce())
                .query(sqlCaptor.capture(), any(Map.class), any(RowMapper.class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce())
                .query(sqlCaptor.capture(), any(Map.class), any(ResultSetExtractor.class));

        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> {
                    assertThat(sql.toLowerCase()).doesNotContain("from workouts");
                    assertThat(sql.toLowerCase()).doesNotContain("join workouts");
                });
    }

    @Test
    void dailyStatsIncludesCardioAggregates() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("total_sessions")).thenReturn(3);
        when(rs.getDouble("total_volume")).thenReturn(1250.0);
        when(rs.getInt("cardio_sessions")).thenReturn(2);
        when(rs.getInt("cardio_duration_seconds")).thenReturn(1800);
        when(rs.getDouble("cardio_calories")).thenReturn(320.0);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(Map.class), any(RowCallbackHandler.class));

        WorkoutDailyStatsDto stats = adapter.getDailyStats(1L, LocalDate.of(2026, 3, 20));

        assertThat(stats.getTotalSessions()).isEqualTo(3);
        assertThat(stats.getTotalVolumeKg()).isEqualTo(1250.0);
        assertThat(stats.getCardioSessions()).isEqualTo(2);
        assertThat(stats.getCardioDurationSeconds()).isEqualTo(1800);
        assertThat(stats.getCardioCalories()).isEqualTo(320.0);
    }
}
