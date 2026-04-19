package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.NutritionJdbcQueryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NutritionMonthlyApiTest {

    private NamedParameterJdbcTemplate jdbc;
    private NutritionJdbcQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        adapter = new NutritionJdbcQueryAdapter(jdbc);
    }

    @Test
    void shouldReturnMonthlyStats_withWeeklyBreakdown() throws Exception {
        Long userId = 1L;
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        // --- mock agg query ---
        when(jdbc.queryForObject(
                anyString(),
                any(Map.class),
                ArgumentMatchers.<RowMapper<NutritionMonthlyStatsDto.NutritionMonthlyStatsDtoBuilder>>any()
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(2);

            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("total_cal")).thenReturn(3000);
            when(rs.getDouble("avg_cal")).thenReturn(100.0);
            when(rs.getDouble("avg_prot")).thenReturn(50.0);
            when(rs.getDouble("avg_fat")).thenReturn(30.0);
            when(rs.getDouble("avg_carb")).thenReturn(120.0);

            return mapper.mapRow(rs, 0);
        });

        // --- mock weekly query (ВАЖНО: RowCallbackHandler) ---
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);

            ResultSet rs = mock(ResultSet.class);

            when(rs.next()).thenReturn(true, true, false);

            when(rs.getDate("week_start"))
                    .thenReturn(java.sql.Date.valueOf("2025-01-01"))
                    .thenReturn(java.sql.Date.valueOf("2025-01-08"));

            when(rs.getInt("total_cal")).thenReturn(1000, 2000);
            when(rs.getDouble("avg_cal")).thenReturn(100.0, 200.0);
            when(rs.getDouble("avg_prot")).thenReturn(50.0, 60.0);
            when(rs.getDouble("avg_fat")).thenReturn(30.0, 40.0);
            when(rs.getDouble("avg_carb")).thenReturn(120.0, 140.0);

            while (rs.next()) {
                handler.processRow(rs);
            }

            return null;
        }).when(jdbc).query(
                anyString(),
                any(Map.class),
                any(RowCallbackHandler.class)
        );

        // --- act ---
        NutritionMonthlyStatsDto result =
                adapter.getMonthlyStats(userId, start, end);

        // --- assert ---
        assertThat(result).isNotNull();
        assertThat(result.getTotalCalories()).isEqualTo(3000);
        assertThat(result.getAvgCalories()).isEqualTo(100.0);

        assertThat(result.getDailyBreakdown()).hasSize(2);
        assertThat(result.getDailyBreakdown())
                .containsKey("2025-01-01")
                .containsKey("2025-01-08");

        // sanity check — что реально вызвался нужный метод
        verify(jdbc).query(anyString(), any(Map.class), any(RowCallbackHandler.class));
    }

    @Test
    void shouldReturnEmptyWeeklyBreakdown_whenNoData() throws Exception {
        Long userId = 1L;
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        // agg
        when(jdbc.queryForObject(anyString(), any(Map.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);

                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getInt("total_cal")).thenReturn(0);
                    when(rs.getDouble(anyString())).thenReturn(0.0);

                    return mapper.mapRow(rs, 0);
                });

        // weekly empty
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);

            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(false);

            while (rs.next()) {
                handler.processRow(rs);
            }

            return null;
        }).when(jdbc).query(
                anyString(),
                any(Map.class),
                any(RowCallbackHandler.class)
        );

        // act
        NutritionMonthlyStatsDto result =
                adapter.getMonthlyStats(userId, start, end);

        // assert
        assertThat(result.getDailyBreakdown()).isEmpty();
        assertThat(result.getTotalCalories()).isZero();
    }
}