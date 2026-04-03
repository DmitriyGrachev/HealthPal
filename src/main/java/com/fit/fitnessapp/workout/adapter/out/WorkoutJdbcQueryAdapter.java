package com.fit.fitnessapp.workout.adapter.out;

import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutWeeklyStatsDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    @Override
    public WorkoutWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        String sql = """
            WITH daily_stats AS (
                SELECT 
                    w.date::date as workout_date,
                    TRIM(TO_CHAR(w.date, 'DAY')) as day_name,
                    COALESCE(SUM(ws.weight * ws.reps), 0) as daily_volume
                FROM workouts w
                LEFT JOIN workout_exercises we ON w.id = we.workout_id
                LEFT JOIN workout_sets ws ON we.id = ws.exercise_id
                WHERE w.user_id = :userId 
                  AND w.date >= :startDate 
                  AND w.date < :endDatePlusOne
                GROUP BY w.id, w.date
            )
            SELECT 
                COUNT(workout_date) as total_sessions,
                COALESCE(SUM(daily_volume), 0) as total_volume
            FROM daily_stats
            """;

        String volumeByDaySql = """
            SELECT 
                TRIM(TO_CHAR(w.date, 'DAY')) as day_name,
                COALESCE(SUM(ws.weight * ws.reps), 0) as daily_volume
            FROM workouts w
            LEFT JOIN workout_exercises we ON w.id = we.workout_id
            LEFT JOIN workout_sets ws ON we.id = ws.exercise_id
            WHERE w.user_id = :userId 
              AND w.date >= :startDate 
              AND w.date < :endDatePlusOne
            GROUP BY day_name
            """;

        Map<String, Object> params = Map.of(
                "userId", userId,
                "startDate", weekStart.atStartOfDay(), // '2026-04-01 00:00'
                "endDatePlusOne", weekEnd.plusDays(1).atStartOfDay() // Строго меньше следующего дня
        );

        // 1. Считаем общие цифры
        WorkoutWeeklyStatsDto.WorkoutWeeklyStatsDtoBuilder builder = WorkoutWeeklyStatsDto.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd);

        jdbc.query(sql, params, rs -> {
            builder.totalSessions(rs.getInt("total_sessions"));
            builder.totalVolumeKg(rs.getDouble("total_volume"));
        });

        // 2. Собираем мапу объемов по дням недели
        Map<String, Double> volumeByDay = jdbc.query(volumeByDaySql, params, rs -> {
            Map<String, Double> map = new java.util.HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("day_name").toUpperCase(), rs.getDouble("daily_volume"));
            }
            return map;
        });

        builder.volumeByDay(volumeByDay != null ? volumeByDay : Map.of());

        return builder.build();
    }

}
