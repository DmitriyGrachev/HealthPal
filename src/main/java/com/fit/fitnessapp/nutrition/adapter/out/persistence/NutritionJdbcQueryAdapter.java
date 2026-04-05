package com.fit.fitnessapp.nutrition.adapter.out.persistence;

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
public class NutritionJdbcQueryAdapter implements NutritionQueryUseCase, NutritionWeeklyApi {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public NutritionDay getDay(Long userId, LocalDate date) {
        String sql = """
            SELECT 
                d.user_id, d.date, 
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

            while (rs.next()) {
                dayFound = true;
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
                return new NutritionDay(userId, date, Collections.emptyList());
            }
            return new NutritionDay(userId, date, entries);
        });
    }

    @Override
    public List<NutritionDaySummary> getCurrentMonthSummary(Long userId) {
        // Определяем первый и последний день текущего месяца
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
                        rs.getInt("date_int"), // берем из базы, а не вычисляем на лету
                        rs.getDouble("calories"),
                        rs.getDouble("protein"),
                        rs.getDouble("fat"),
                        rs.getDouble("carbohydrate")
                ));
    }
    @Override
    public NutritionWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        // Запрос 1: Считаем общие суммы и средние значения за неделю
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

        // Запрос 2: Получаем данные по конкретным дням для корреляции
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

        NutritionWeeklyStatsDto.NutritionWeeklyStatsDtoBuilder builder = NutritionWeeklyStatsDto.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd);

        jdbc.query(aggSql, params, rs -> {
            builder.totalCalories(rs.getInt("total_cal"));
            builder.avgCalories(rs.getDouble("avg_cal"));
            builder.avgProtein(rs.getDouble("avg_prot"));
            builder.avgFat(rs.getDouble("avg_fat"));
            builder.avgCarbs(rs.getDouble("avg_carb"));
        });

        Map<String, NutritionWeeklyStatsDto.DailyMacrosDto> dailyBreakdown = jdbc.query(dailySql, params, rs -> {
            Map<String, NutritionWeeklyStatsDto.DailyMacrosDto> map = new java.util.HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("day_name").toUpperCase(),
                        NutritionWeeklyStatsDto.DailyMacrosDto.builder()
                                .calories(rs.getInt("cal"))
                                .protein(rs.getDouble("prot"))
                                .fat(rs.getDouble("fat"))
                                .carbs(rs.getDouble("carb"))
                                .build()
                );
            }
            return map;
        });

        builder.dailyBreakdown(dailyBreakdown != null ? dailyBreakdown : Map.of());

        return builder.build();
    }
}
