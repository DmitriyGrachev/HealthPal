package com.fit.fitnessapp.jdbc.workout;

import com.fit.fitnessapp.workout.adapter.out.WorkoutJdbcQueryAdapter;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkoutQueryUseCaseTest {

    @Test
    void weeklySummaryMapsJdbcRowsToWorkoutSummaryDtos() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        WorkoutJdbcQueryAdapter adapter = new WorkoutJdbcQueryAdapter(
                jdbc,
                Clock.fixed(Instant.parse("2026-03-18T12:00:00Z"), ZoneOffset.UTC));

        ResultSet rs = mock(ResultSet.class);
        LocalDateTime weekStart = LocalDateTime.of(2026, 3, 16, 0, 0);
        when(rs.getString("exercise_name")).thenReturn("Bench Press");
        when(rs.getLong("weekly_reps_sum")).thenReturn(6L);
        when(rs.getDouble("weekly_weight_sum")).thenReturn(130.0);
        when(rs.getTimestamp("week")).thenReturn(Timestamp.valueOf(weekStart));

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<WorkoutSummaryWeeklyDto> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<WorkoutSummaryWeeklyDto> result = adapter.getAllWorkoutSummaryThisWeek(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getExerciseName()).isEqualTo("Bench Press");
        assertThat(result.getFirst().getTotalReps()).isEqualTo(6L);
        assertThat(result.getFirst().getTotalWeight()).isEqualTo(130.0);
        assertThat(result.getFirst().getDate()).isEqualTo(weekStart);

        verify(jdbc).query(anyString(), any(Map.class), any(RowMapper.class));
    }
}
