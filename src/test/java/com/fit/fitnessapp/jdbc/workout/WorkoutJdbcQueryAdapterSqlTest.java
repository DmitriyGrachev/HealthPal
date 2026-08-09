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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-08T12:00:00Z"), ZoneOffset.UTC);
    private final WorkoutJdbcQueryAdapter adapter = new WorkoutJdbcQueryAdapter(jdbc, clock);

    @Test
    void currentWeekSummaryUsesClockAndExclusiveEndBoundary() {
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenReturn(List.of());

        adapter.getAllWorkoutSummaryThisWeek(42L);

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue())
                .containsEntry("userId", 42L)
                .containsEntry("startDate", LocalDate.of(2026, 7, 6).atStartOfDay())
                .containsEntry("endDate", LocalDate.of(2026, 7, 13).atStartOfDay());
    }

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

    @Test
    void weeklyStatsIncludesCardioAggregates() throws Exception {
        ResultSet aggregate = mock(ResultSet.class);
        when(aggregate.getInt("total_sessions")).thenReturn(2);
        when(aggregate.getDouble("total_volume")).thenReturn(1735.0);
        when(aggregate.getInt("cardio_sessions")).thenReturn(1);
        when(aggregate.getInt("cardio_duration_seconds")).thenReturn(1800);
        when(aggregate.getDouble("cardio_calories")).thenReturn(320.0);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(aggregate);
            return null;
        }).when(jdbc).query(anyString(), any(Map.class), any(RowCallbackHandler.class));
        when(jdbc.query(anyString(), any(Map.class), any(ResultSetExtractor.class)))
                .thenReturn(Map.of("MONDAY", 1375.0));

        var stats = adapter.getWeeklyStats(
                1L,
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 12));

        assertThat(stats.getTotalSessions()).isEqualTo(2);
        assertThat(stats.getTotalVolumeKg()).isEqualTo(1735.0);
        assertThat(stats.getCardioSessions()).isEqualTo(1);
        assertThat(stats.getCardioDurationSeconds()).isEqualTo(1800);
        assertThat(stats.getCardioCalories()).isEqualTo(320.0);
    }

    @Test
    void monthlyStatsIncludesCardioAggregates() throws Exception {
        ResultSet aggregate = mock(ResultSet.class);
        when(aggregate.getInt("total_sessions")).thenReturn(8);
        when(aggregate.getDouble("total_volume")).thenReturn(52000.0);
        when(aggregate.getDouble("avg_volume")).thenReturn(6500.0);
        when(aggregate.getInt("cardio_sessions")).thenReturn(4);
        when(aggregate.getInt("cardio_duration_seconds")).thenReturn(7200);
        when(aggregate.getDouble("cardio_calories")).thenReturn(1280.0);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(aggregate);
            return null;
        }).when(jdbc).query(anyString(), any(Map.class), any(RowCallbackHandler.class));
        when(jdbc.query(anyString(), any(Map.class), any(ResultSetExtractor.class)))
                .thenReturn(Map.of("2026-07-06", 4000.0));

        var stats = adapter.getMonthlyStats(
                1L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        assertThat(stats.getTotalSessions()).isEqualTo(8);
        assertThat(stats.getTotalVolumeKg()).isEqualTo(52000.0);
        assertThat(stats.getAvgVolumePerSession()).isEqualTo(6500.0);
        assertThat(stats.getCardioSessions()).isEqualTo(4);
        assertThat(stats.getCardioDurationSeconds()).isEqualTo(7200);
        assertThat(stats.getCardioCalories()).isEqualTo(1280.0);
    }
}
