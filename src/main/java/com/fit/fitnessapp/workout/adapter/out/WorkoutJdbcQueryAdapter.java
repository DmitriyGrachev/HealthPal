package com.fit.fitnessapp.workout.adapter.out;

import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkoutJdbcQueryAdapter implements WorkoutQueryUseCase {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<WorkoutSummaryDto> getAllWorkoutSummaryByUser(Long userId) {
        String sql = """
                SELECT\s
                    w.id,
                    w.date,
                    COUNT(DISTINCT we.id)                           AS total_exercises,
                    COUNT(ws.id)                                    AS total_sets,
                    SUM(ws.weight * ws.reps)                        AS total_volume,
                    STRING_AGG(DISTINCT we.exercise_name, ', ')     AS exercise_names_preview
                FROM workouts w
                LEFT JOIN workout_exercises we ON w.id = we.workout_id
                LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                WHERE w.user_id = :userId
                GROUP BY w.id, w.date
                ORDER BY w.date DESC
                """;
        return jdbc.query(sql, Map.of("userId", userId), (rs, rowNum) ->
                WorkoutSummaryDto.builder()
                        .id(rs.getLong("id"))
                        .date(rs.getTimestamp("date").toLocalDateTime())
                        .totalExercises(rs.getInt("total_exercises"))
                        .totalSets(rs.getInt("total_sets"))
                        .totalVolume(rs.getDouble("total_volume"))
                        .exerciseNamesPreview(rs.getString("exercise_names_preview"))
                        .build()
        );
    }
}
