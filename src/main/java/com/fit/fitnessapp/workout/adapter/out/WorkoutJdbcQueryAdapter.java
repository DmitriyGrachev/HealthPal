package com.fit.fitnessapp.workout.adapter.out;

import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    public List<WorkoutSummaryWeeklyDto> getAllWorkoutSummaryThisWeek(Long userId){

        LocalDateTime localDateTime = LocalDateTime.now().minusWeeks(1);
        return getWorkoutSummary(userId,localDateTime);
    }

    @Override
    public List<WorkoutSummaryWeeklyDto> getWorkoutSummaryLastTwoWeeks(Long userId) {
        LocalDateTime localDateTime = LocalDateTime.now().minusWeeks(2);
        return getWorkoutSummary(userId, localDateTime);
    }

    @Override
    public List<WorkoutSummaryWeeklyDto> getWorkoutSummaryThisMonth(Long userId) {
        LocalDateTime localDateTime = LocalDateTime.now().minusMonths(1);
        return getWorkoutSummary(userId, localDateTime);
    }

    private List<WorkoutSummaryWeeklyDto> getWorkoutSummary(Long userId, LocalDateTime startDate) {
        String sql = """
                select we.exercise_name,sum(ws.weight) as weekly_weight_sum,sum(ws.reps) as weekly_reps_sum, DATE_TRUNC('week', w.date) as week from workouts as w
                left join public.workout_exercises we on w.id = we.workout_id
                left join public.workout_sets ws on we.id = ws.exercise_id
                where w.date >= :startDate and w.user_Id = :userId
                group by week,we.exercise_name
                order by we.exercise_name, week;
                """;

        return jdbc.query(sql, Map.of("userId", userId,"startDate",startDate),(rs,rowNum)->
                WorkoutSummaryWeeklyDto.builder()
                        .exerciseName(rs.getString("exercise_name"))
                        .totalReps(rs.getLong("weekly_reps_sum"))
                        .totalWeight(rs.getDouble("weekly_weight_sum"))
                        .date(rs.getTimestamp("week").toLocalDateTime())
                        .build()
        );
    }

}
