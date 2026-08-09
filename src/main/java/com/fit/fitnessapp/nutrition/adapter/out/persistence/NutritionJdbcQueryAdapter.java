package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.NutritionMonthlyApi;
import com.fit.fitnessapp.nutrition.NutritionMonthlyStatsDto;
import com.fit.fitnessapp.nutrition.NutritionWeeklyApi;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.NutritionWeeklyStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Component
@RequiredArgsConstructor
public class NutritionJdbcQueryAdapter implements NutritionQueryUseCase, NutritionWeeklyApi, NutritionMonthlyApi {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public NutritionDay getDay(Long userId, LocalDate date) {
        String sql = """
            SELECT 
                d.user_id, d.date,
                d.calories AS day_calories,
                d.protein AS day_protein,
                d.fat AS day_fat,
                d.carbohydrate AS day_carbohydrate,
                f.external_food_id, f.external_entry_id, f.name, 
                f.meal_type, f.calories, f.protein, f.fat, f.carbohydrate 
            FROM fatsecret_day d
            LEFT JOIN fatsecret_food f ON d.id = f.day_id
            WHERE d.user_id = :userId AND d.date = :date
            ORDER BY f.meal_type ASC, f.id ASC
            """;

        return jdbc.query(sql, Map.of("userId", userId, "date", date), rs -> {
            List<FoodEntry> entries = new ArrayList<>();
            boolean dayFound = false;
            int totalCalories = 0;
            double totalProtein = 0.0;
            double totalFat = 0.0;
            double totalCarbohydrate = 0.0;

            while (rs.next()) {
                dayFound = true;
                totalCalories = rs.getInt("day_calories");
                totalProtein = rs.getDouble("day_protein");
                totalFat = rs.getDouble("day_fat");
                totalCarbohydrate = rs.getDouble("day_carbohydrate");
                if (rs.getObject("external_food_id") != null) {
                    entries.add(new FoodEntry(
                            rs.getLong("external_food_id"),
                            rs.getLong("external_entry_id"),
                            rs.getString("name"),
                            rs.getString("meal_type"),
                            rs.getInt("calories"),
                            rs.getDouble("protein"),
                            rs.getDouble("fat"),
                            rs.getDouble("carbohydrate")
                    ));
                }
            }

            if (!dayFound) {
                return NutritionDay.missingSync(userId, date);
            }
            return new NutritionDay(
                    userId,
                    date,
                    entries,
                    totalCalories,
                    totalProtein,
                    totalFat,
                    totalCarbohydrate);
        });
    }

    @Override
    public List<NutritionDaySummary> getCurrentMonthSummary(Long userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();

        return getDateRange(userId, start, end);
    }

    @Override
    public List<NutritionDaySummary> getDateRange(Long userId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT date, date_int, calories, protein, fat, carbohydrate
            FROM fatsecret_day
            WHERE user_id = :userId
              AND date BETWEEN :from AND :to
            ORDER BY date
            """;

        return jdbc.query(sql,
                Map.of("userId", userId, "from", from, "to", to),
                (rs, rowNum) -> new NutritionDaySummary(
                        userId,
                        rs.getDate("date").toLocalDate(),
                        rs.getInt("date_int"),
                        rs.getDouble("calories"),
                        rs.getDouble("protein"),
                        rs.getDouble("fat"),
                        rs.getDouble("carbohydrate")
                ));
    }

    @Override
    public NutritionWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        String aggSql = """
            SELECT 
                COALESCE(SUM(calories), 0) as total_cal,
                COALESCE(AVG(calories), 0) as avg_cal,
                COALESCE(AVG(protein), 0) as avg_prot,
                COALESCE(AVG(fat), 0) as avg_fat,
                COALESCE(AVG(carbohydrate), 0) as avg_carb
            FROM fatsecret_day
            WHERE user_id = :userId 
              AND date >= :startDate 
              AND date < :endDatePlusOne
            """;

        String dailySql = """
            SELECT 
                TRIM(TO_CHAR(date, 'DAY')) as day_name,
                COALESCE(calories, 0) as cal,
                COALESCE(protein, 0) as prot,
                COALESCE(fat, 0) as fat,
                COALESCE(carbohydrate, 0) as carb
            FROM fatsecret_day
            WHERE user_id = :userId 
              AND date >= :startDate 
              AND date < :endDatePlusOne
            """;

        Map<String, Object> params = Map.of(
                "userId", userId,
                "startDate", weekStart,
                "endDatePlusOne", weekEnd.plusDays(1)
        );

        final int[] totalCal = {0};
        final double[] avgCal = {0.0};
        final double[] avgProt = {0.0};
        final double[] avgFat = {0.0};
        final double[] avgCarb = {0.0};

        jdbc.query(aggSql, params, rs -> {
            totalCal[0] = rs.getInt("total_cal");
            avgCal[0] = rs.getDouble("avg_cal");
            avgProt[0] = rs.getDouble("avg_prot");
            avgFat[0] = rs.getDouble("avg_fat");
            avgCarb[0] = rs.getDouble("avg_carb");
        });

        Map<String, NutritionWeeklyStatsDto.DailyMacrosDto> dailyBreakdown = jdbc.query(dailySql, params, rs -> {
            Map<String, NutritionWeeklyStatsDto.DailyMacrosDto> map = new java.util.HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("day_name").toUpperCase(),
                        new NutritionWeeklyStatsDto.DailyMacrosDto(
                                rs.getInt("cal"),
                                rs.getDouble("prot"),
                                rs.getDouble("fat"),
                                rs.getDouble("carb")
                        )
                );
            }
            return map;
        });

        return new NutritionWeeklyStatsDto(
                weekStart,
                weekEnd,
                totalCal[0],
                avgCal[0],
                avgProt[0],
                avgFat[0],
                avgCarb[0],
                dailyBreakdown != null ? dailyBreakdown : Map.of()
        );
    }

    @Override
    public NutritionMonthlyStatsDto getMonthlyStats(Long userId, LocalDate monthStart, LocalDate monthEnd) {

        String aggSql = """
        SELECT 
            COALESCE(SUM(calories), 0)  AS total_cal,
            COALESCE(AVG(calories), 0)  AS avg_cal,
            COALESCE(AVG(protein), 0)   AS avg_prot,
            COALESCE(AVG(fat), 0)       AS avg_fat,
            COALESCE(AVG(carbohydrate), 0) AS avg_carb,
            COUNT(*)                    AS days_tracked
        FROM fatsecret_day
        WHERE user_id = :userId
          AND date >= :startDate
          AND date < :endDatePlusOne
        """;

        String dailySql = """
        SELECT 
            CAST(date AS TEXT) AS day_key,
            COALESCE(calories, 0)    AS cal,
            COALESCE(protein, 0)     AS prot,
            COALESCE(fat, 0)         AS fat,
            COALESCE(carbohydrate, 0) AS carb
        FROM fatsecret_day
        WHERE user_id = :userId
          AND date >= :startDate
          AND date < :endDatePlusOne
        ORDER BY date
        """;

        Map<String, Object> params = Map.of(
                "userId", userId,
                "startDate", monthStart,
                "endDatePlusOne", monthEnd.plusDays(1)
        );

        final int[] totalCal = {0};
        final double[] avgCal = {0.0};
        final double[] avgProt = {0.0};
        final double[] avgFat = {0.0};
        final double[] avgCarb = {0.0};
        final int[] daysTracked = {0};

        jdbc.query(aggSql, params, rs -> {
            totalCal[0] = rs.getInt("total_cal");
            avgCal[0] = rs.getDouble("avg_cal");
            avgProt[0] = rs.getDouble("avg_prot");
            avgFat[0] = rs.getDouble("avg_fat");
            avgCarb[0] = rs.getDouble("avg_carb");
            daysTracked[0] = rs.getInt("days_tracked");
        });

        Map<String, NutritionMonthlyStatsDto.DailyMacrosDto> dailyBreakdown = jdbc.query(dailySql, params, rs -> {
            Map<String, NutritionMonthlyStatsDto.DailyMacrosDto> map = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                map.put(rs.getString("day_key"),
                        new NutritionMonthlyStatsDto.DailyMacrosDto(
                                rs.getInt("cal"),
                                rs.getDouble("prot"),
                                rs.getDouble("fat"),
                                rs.getDouble("carb")
                        )
                );
            }
            return map;
        });

        return new NutritionMonthlyStatsDto(
                monthStart,
                monthEnd,
                totalCal[0],
                avgCal[0],
                avgProt[0],
                avgFat[0],
                avgCarb[0],
                daysTracked[0],
                dailyBreakdown != null ? dailyBreakdown : Map.of()
        );
    }
}
