package com.fit.fitnessapp.workout.adapter.out;

import com.fit.fitnessapp.workout.WorkoutDailyApi;
import com.fit.fitnessapp.workout.WorkoutDailyStatsDto;
import com.fit.fitnessapp.workout.WorkoutMonthlyApi;
import com.fit.fitnessapp.workout.WorkoutMonthlyStatsDto;
import com.fit.fitnessapp.workout.WorkoutWeeklyApi;
import com.fit.fitnessapp.workout.WorkoutWeeklyStatsDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkoutJdbcQueryAdapter implements WorkoutQueryUseCase, WorkoutDailyApi, WorkoutWeeklyApi, WorkoutMonthlyApi {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public List<WorkoutSummaryDto> getAllWorkoutSummaryByUser(Long userId) {
        String sql = """
                SELECT
                    w.id,
                    w.date,
                    COUNT(DISTINCT we.id)                       AS total_exercises,
                    COUNT(ws.id)                                AS total_sets,
                    SUM(ws.weight * ws.reps)                    AS total_volume,
                    STRING_AGG(DISTINCT we.exercise_name, ', ') AS exercise_names_preview
                FROM workout w
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

    @Override
    public List<WorkoutSummaryWeeklyDto> getAllWorkoutSummaryThisWeek(Long userId) {
        com.fit.fitnessapp.domain.DateRange range =
                com.fit.fitnessapp.domain.DateRange.isoWeek(LocalDate.now(clock));
        return getWorkoutSummary(userId, range.startDate().atStartOfDay(), range.endDate().plusDays(1).atStartOfDay());
    }

    @Override
    public List<WorkoutSummaryWeeklyDto> getWorkoutSummaryLastTwoWeeks(Long userId) {
        LocalDate today = LocalDate.now(clock);
        com.fit.fitnessapp.domain.DateRange range =
                com.fit.fitnessapp.domain.DateRange.isoWeek(today.minusWeeks(1));
        com.fit.fitnessapp.domain.DateRange currentWeek =
                com.fit.fitnessapp.domain.DateRange.isoWeek(today);
        return getWorkoutSummary(userId, range.startDate().atStartOfDay(), currentWeek.endDate().plusDays(1).atStartOfDay());
    }

    @Override
    public List<WorkoutSummaryWeeklyDto> getWorkoutSummaryThisMonth(Long userId) {
        com.fit.fitnessapp.domain.DateRange range =
                com.fit.fitnessapp.domain.DateRange.calendarMonth(YearMonth.now(clock));
        return getWorkoutSummary(userId, range.startDate().atStartOfDay(), range.endDate().plusDays(1).atStartOfDay());
    }

    private List<WorkoutSummaryWeeklyDto> getWorkoutSummary(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
                SELECT
                    we.exercise_name,
                    SUM(ws.weight)             AS weekly_weight_sum,
                    SUM(ws.reps)               AS weekly_reps_sum,
                    DATE_TRUNC('week', w.date) AS week
                FROM workout w
                LEFT JOIN workout_exercises we ON w.id = we.workout_id
                LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                WHERE w.date >= :startDate
                  AND w.date < :endDate
                  AND w.user_id = :userId
                GROUP BY week, we.exercise_name
                ORDER BY we.exercise_name, week
                """;

        return jdbc.query(sql, Map.of("userId", userId, "startDate", startDate, "endDate", endDate), (rs, rowNum) ->
                WorkoutSummaryWeeklyDto.builder()
                        .exerciseName(rs.getString("exercise_name"))
                        .totalReps(rs.getLong("weekly_reps_sum"))
                        .totalWeight(rs.getDouble("weekly_weight_sum"))
                        .date(rs.getTimestamp("week").toLocalDateTime())
                        .build()
        );
    }

    @Override
    public WorkoutDailyStatsDto getDailyStats(Long userId, LocalDate date) {
        String sql = """
                WITH strength_stats AS (
                    SELECT
                        COUNT(DISTINCT w.id)                  AS strength_sessions,
                        COALESCE(SUM(ws.weight * ws.reps), 0) AS total_volume
                    FROM workout w
                    LEFT JOIN workout_exercises we ON w.id = we.workout_id
                    LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                    WHERE w.user_id = :userId
                      AND w.date >= :startDate
                      AND w.date < :endDatePlusOne
                ),
                cardio_stats AS (
                    SELECT
                        COUNT(c.id)                                  AS cardio_sessions,
                        COALESCE(SUM(c.duration_seconds), 0)         AS cardio_duration_seconds,
                        COALESCE(SUM(c.calories), 0)                 AS cardio_calories
                    FROM workout_cardio c
                    WHERE c.user_id = :userId
                      AND c.date >= :startDate
                      AND c.date < :endDatePlusOne
                )
                SELECT
                    strength_stats.strength_sessions + cardio_stats.cardio_sessions AS total_sessions,
                    strength_stats.total_volume                                     AS total_volume,
                    cardio_stats.cardio_sessions                                    AS cardio_sessions,
                    cardio_stats.cardio_duration_seconds                            AS cardio_duration_seconds,
                    cardio_stats.cardio_calories                                    AS cardio_calories
                FROM strength_stats
                CROSS JOIN cardio_stats
                """;

        final int[] totalSessions = {0};
        final double[] totalVolumeKg = {0.0};
        final int[] cardioSessions = {0};
        final int[] cardioDurationSeconds = {0};
        final double[] cardioCalories = {0.0};

        jdbc.query(sql, statsParams(userId, date, date), rs -> {
            totalSessions[0] = rs.getInt("total_sessions");
            totalVolumeKg[0] = rs.getDouble("total_volume");
            cardioSessions[0] = rs.getInt("cardio_sessions");
            cardioDurationSeconds[0] = rs.getInt("cardio_duration_seconds");
            cardioCalories[0] = rs.getDouble("cardio_calories");
        });

        return new WorkoutDailyStatsDto(
                date,
                totalSessions[0],
                totalVolumeKg[0],
                cardioSessions[0],
                cardioDurationSeconds[0],
                cardioCalories[0]
        );
    }

    @Override
    public WorkoutWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        String aggregateSql = """
                WITH daily_stats AS (
                    SELECT
                        w.date::date                          AS workout_date,
                        COALESCE(SUM(ws.weight * ws.reps), 0) AS daily_volume
                    FROM workout w
                    LEFT JOIN workout_exercises we ON w.id = we.workout_id
                    LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                    WHERE w.user_id = :userId
                      AND w.date >= :startDate
                      AND w.date < :endDatePlusOne
                    GROUP BY w.id, w.date
                ),
                strength_stats AS (
                    SELECT
                        COUNT(workout_date)            AS total_sessions,
                        COALESCE(SUM(daily_volume), 0) AS total_volume
                    FROM daily_stats
                ),
                cardio_stats AS (
                    SELECT
                        COUNT(c.id)                          AS cardio_sessions,
                        COALESCE(SUM(c.duration_seconds), 0) AS cardio_duration_seconds,
                        COALESCE(SUM(c.calories), 0)         AS cardio_calories
                    FROM workout_cardio c
                    WHERE c.user_id = :userId
                      AND c.date >= :startDate
                      AND c.date < :endDatePlusOne
                )
                SELECT
                    strength_stats.total_sessions              AS total_sessions,
                    strength_stats.total_volume                AS total_volume,
                    cardio_stats.cardio_sessions               AS cardio_sessions,
                    cardio_stats.cardio_duration_seconds       AS cardio_duration_seconds,
                    cardio_stats.cardio_calories               AS cardio_calories
                FROM strength_stats
                CROSS JOIN cardio_stats
                """;

        String volumeByDaySql = """
                SELECT
                    TRIM(TO_CHAR(w.date, 'DAY'))          AS day_name,
                    COALESCE(SUM(ws.weight * ws.reps), 0) AS daily_volume
                FROM workout w
                LEFT JOIN workout_exercises we ON w.id = we.workout_id
                LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                WHERE w.user_id = :userId
                  AND w.date >= :startDate
                  AND w.date < :endDatePlusOne
                GROUP BY day_name
                """;

        Map<String, Object> params = statsParams(userId, weekStart, weekEnd);

        final int[] totalSessions = {0};
        final double[] totalVolumeKg = {0.0};
        final int[] cardioSessions = {0};
        final int[] cardioDurationSeconds = {0};
        final double[] cardioCalories = {0.0};

        jdbc.query(aggregateSql, params, rs -> {
            totalSessions[0] = rs.getInt("total_sessions");
            totalVolumeKg[0] = rs.getDouble("total_volume");
            cardioSessions[0] = rs.getInt("cardio_sessions");
            cardioDurationSeconds[0] = rs.getInt("cardio_duration_seconds");
            cardioCalories[0] = rs.getDouble("cardio_calories");
        });

        Map<String, Double> volumeByDay = jdbc.query(volumeByDaySql, params, rs -> {
            Map<String, Double> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("day_name").toUpperCase(), rs.getDouble("daily_volume"));
            }
            return map;
        });

        return new WorkoutWeeklyStatsDto(
                weekStart,
                weekEnd,
                totalSessions[0],
                totalVolumeKg[0],
                cardioSessions[0],
                cardioDurationSeconds[0],
                cardioCalories[0],
                volumeByDay != null ? volumeByDay : Map.of()
        );
    }

    @Override
    public WorkoutMonthlyStatsDto getMonthlyStats(Long userId, LocalDate monthStart, LocalDate monthEnd) {
        String aggregateSql = """
                WITH daily_stats AS (
                    SELECT
                        CAST(w.date AS DATE)                  AS workout_date,
                        COALESCE(SUM(ws.weight * ws.reps), 0) AS daily_volume
                    FROM workout w
                    LEFT JOIN workout_exercises we ON w.id = we.workout_id
                    LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                    WHERE w.user_id = :userId
                      AND w.date >= :startDate
                      AND w.date < :endDatePlusOne
                    GROUP BY w.id, w.date
                ),
                strength_stats AS (
                    SELECT
                        COUNT(workout_date)            AS total_sessions,
                        COALESCE(SUM(daily_volume), 0) AS total_volume,
                        COALESCE(AVG(daily_volume), 0) AS avg_volume
                    FROM daily_stats
                ),
                cardio_stats AS (
                    SELECT
                        COUNT(c.id)                          AS cardio_sessions,
                        COALESCE(SUM(c.duration_seconds), 0) AS cardio_duration_seconds,
                        COALESCE(SUM(c.calories), 0)         AS cardio_calories
                    FROM workout_cardio c
                    WHERE c.user_id = :userId
                      AND c.date >= :startDate
                      AND c.date < :endDatePlusOne
                )
                SELECT
                    strength_stats.total_sessions              AS total_sessions,
                    strength_stats.total_volume                AS total_volume,
                    strength_stats.avg_volume                  AS avg_volume,
                    cardio_stats.cardio_sessions               AS cardio_sessions,
                    cardio_stats.cardio_duration_seconds       AS cardio_duration_seconds,
                    cardio_stats.cardio_calories               AS cardio_calories
                FROM strength_stats
                CROSS JOIN cardio_stats
                """;

        String volumeByDaySql = """
                SELECT
                    CAST(CAST(w.date AS DATE) AS TEXT)    AS day_key,
                    COALESCE(SUM(ws.weight * ws.reps), 0) AS daily_volume
                FROM workout w
                LEFT JOIN workout_exercises we ON w.id = we.workout_id
                LEFT JOIN workout_sets ws      ON we.id = ws.exercise_id
                WHERE w.user_id = :userId
                  AND w.date >= :startDate
                  AND w.date < :endDatePlusOne
                GROUP BY CAST(w.date AS DATE)
                ORDER BY CAST(w.date AS DATE)
                """;

        Map<String, Object> params = statsParams(userId, monthStart, monthEnd);

        final int[] totalSessions = {0};
        final double[] totalVolumeKg = {0.0};
        final double[] avgVolumePerSession = {0.0};
        final int[] cardioSessions = {0};
        final int[] cardioDurationSeconds = {0};
        final double[] cardioCalories = {0.0};

        jdbc.query(aggregateSql, params, rs -> {
            totalSessions[0] = rs.getInt("total_sessions");
            totalVolumeKg[0] = rs.getDouble("total_volume");
            avgVolumePerSession[0] = rs.getDouble("avg_volume");
            cardioSessions[0] = rs.getInt("cardio_sessions");
            cardioDurationSeconds[0] = rs.getInt("cardio_duration_seconds");
            cardioCalories[0] = rs.getDouble("cardio_calories");
        });

        Map<String, Double> volumeByDay = jdbc.query(volumeByDaySql, params, rs -> {
            Map<String, Double> map = new LinkedHashMap<>();
            while (rs.next()) {
                map.put(rs.getString("day_key"), rs.getDouble("daily_volume"));
            }
            return map;
        });

        return new WorkoutMonthlyStatsDto(
                monthStart,
                monthEnd,
                totalSessions[0],
                totalVolumeKg[0],
                avgVolumePerSession[0],
                cardioSessions[0],
                cardioDurationSeconds[0],
                cardioCalories[0],
                volumeByDay != null ? volumeByDay : Map.of()
        );
    }

    private Map<String, Object> statsParams(Long userId, LocalDate start, LocalDate end) {
        return Map.of(
                "userId", userId,
                "startDate", start.atStartOfDay(),
                "endDatePlusOne", end.plusDays(1).atStartOfDay()
        );
    }
}
