package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionQueryPort;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NutritionJdbcQueryAdapter implements NutritionQueryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Optional<NutritionDay> getDayByDate(Long userId, LocalDate date) {
        return Optional.empty();
    }

    @Override
    public List<NutritionDaySummary> getSummaryByDateRange(
            Long userId, LocalDate from, LocalDate to) {

        String sql = """
            SELECT date, calories, protein, fat, carbohydrate
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
                        (int) rs.getDate("date").toLocalDate().toEpochDay(),
                        rs.getDouble("calories"),
                        rs.getDouble("protein"),
                        rs.getDouble("fat"),
                        rs.getDouble("carbohydrate")
                ));
    }
}
