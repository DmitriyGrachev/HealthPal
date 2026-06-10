package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.NutritionJdbcQueryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionMonthlyApiTest {

    private NamedParameterJdbcTemplate jdbc;
    private NutritionJdbcQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        adapter = new NutritionJdbcQueryAdapter(jdbc);
    }

    @Test
    void shouldReturnMonthlyStatsWithDailyBreakdown() throws Exception {
        Long userId = 1L;
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        mockMonthlyQueries(
                aggregateResultSet(3000, 100.0, 50.0, 30.0, 120.0, 2),
                dailyBreakdownResultSet()
        );

        NutritionMonthlyStatsDto result = adapter.getMonthlyStats(userId, start, end);

        assertThat(result.getMonthStart()).isEqualTo(start);
        assertThat(result.getMonthEnd()).isEqualTo(end);
        assertThat(result.getTotalCalories()).isEqualTo(3000);
        assertThat(result.getAvgCalories()).isEqualTo(100.0);
        assertThat(result.getAvgProtein()).isEqualTo(50.0);
        assertThat(result.getAvgFat()).isEqualTo(30.0);
        assertThat(result.getAvgCarbs()).isEqualTo(120.0);
        assertThat(result.getDaysTracked()).isEqualTo(2);
        assertThat(result.getDailyBreakdown()).containsOnlyKeys("2025-01-01", "2025-01-08");
        assertThat(result.getDailyBreakdown().get("2025-01-08").getCalories()).isEqualTo(2000);

        verify(jdbc).query(anyString(), anyMap(), any(RowCallbackHandler.class));
        verify(jdbc).query(anyString(), anyMap(), any(ResultSetExtractor.class));
    }

    @Test
    void shouldReturnEmptyDailyBreakdownWhenNoRowsExist() throws Exception {
        mockMonthlyQueries(
                aggregateResultSet(0, 0.0, 0.0, 0.0, 0.0, 0),
                emptyDailyBreakdownResultSet()
        );

        NutritionMonthlyStatsDto result = adapter.getMonthlyStats(
                1L,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31)
        );

        assertThat(result.getTotalCalories()).isZero();
        assertThat(result.getDaysTracked()).isZero();
        assertThat(result.getDailyBreakdown()).isEmpty();
    }

    private void mockMonthlyQueries(ResultSet aggregateResultSet, ResultSet dailyResultSet) {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(aggregateResultSet);
            return null;
        }).when(jdbc).query(anyString(), anyMap(), any(RowCallbackHandler.class));

        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(2);
            return extractor.extractData(dailyResultSet);
        }).when(jdbc).query(anyString(), anyMap(), any(ResultSetExtractor.class));
    }

    private ResultSet aggregateResultSet(
            int totalCalories,
            double avgCalories,
            double avgProtein,
            double avgFat,
            double avgCarbs,
            int daysTracked) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("total_cal")).thenReturn(totalCalories);
        when(rs.getDouble("avg_cal")).thenReturn(avgCalories);
        when(rs.getDouble("avg_prot")).thenReturn(avgProtein);
        when(rs.getDouble("avg_fat")).thenReturn(avgFat);
        when(rs.getDouble("avg_carb")).thenReturn(avgCarbs);
        when(rs.getInt("days_tracked")).thenReturn(daysTracked);
        return rs;
    }

    private ResultSet dailyBreakdownResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("day_key")).thenReturn("2025-01-01", "2025-01-08");
        when(rs.getInt("cal")).thenReturn(1000, 2000);
        when(rs.getDouble("prot")).thenReturn(50.0, 60.0);
        when(rs.getDouble("fat")).thenReturn(30.0, 40.0);
        when(rs.getDouble("carb")).thenReturn(120.0, 140.0);
        return rs;
    }

    private ResultSet emptyDailyBreakdownResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        return rs;
    }
}
