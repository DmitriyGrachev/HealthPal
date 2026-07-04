package com.fit.fitnessapp.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class AiPromptRendererTest {

    private final AiPromptRenderer renderer = new AiPromptRenderer();

    @Test
    void rendersDailyInsightPromptWithFixtureContext() {
        String prompt = renderer.render("daily-insight-v1.md", Map.of(
                "memoriesText", "PERMANENT USER FACTS:\n- lactose intolerance",
                "recentInsights", "No previous insights.",
                "totalCalories", 2_100,
                "protein", "155.0",
                "fat", "70.0",
                "carbs", "220.0"
        ));

        assertThat(prompt)
                .contains("name: daily-insight")
                .contains("version: v1")
                .contains("lactose intolerance")
                .contains("Calories: 2100")
                .contains("Protein: 155.0g")
                .doesNotContain("{{");
    }

    @Test
    void rendersWeeklyReportPromptWithFixtureContext() {
        String prompt = renderer.render("weekly-report-v1.md", Map.ofEntries(
                entry("weekStart", "2026-03-09"),
                entry("weekEnd", "2026-03-15"),
                entry("memoriesText", "PATTERNS AND HISTORY:\n- low sleep reduces training volume"),
                entry("recentInsights", "[DAILY 2026-03-14] Protein was low"),
                entry("userContext", "- Age: 34, gender: male, primary goal: cut"),
                entry("totalCalories", 14_000),
                entry("avgCalories", "2000.0"),
                entry("avgProtein", "145.0"),
                entry("avgFat", "65.0"),
                entry("avgCarbs", "210.0"),
                entry("nutritionText", "- MONDAY: 2000 kcal"),
                entry("totalSessions", 4),
                entry("totalVolumeKg", "12500.0"),
                entry("workoutText", "- MONDAY: 5000.0 kg")
        ));

        assertThat(prompt)
                .contains("name: weekly-report")
                .contains("version: v1")
                .contains("2026-03-09 - 2026-03-15")
                .contains("low sleep reduces training volume")
                .contains("total sessions: 4")
                .doesNotContain("{{");
    }

    @Test
    void rendersMonthlyReportPromptWithFixtureContext() {
        String prompt = renderer.render("monthly-report-v1.md", Map.ofEntries(
                entry("monthStart", "2026-03-01"),
                entry("monthEnd", "2026-03-31"),
                entry("memoriesText", "PERMANENT USER FACTS:\n- prefers morning workouts"),
                entry("recentInsights", "[WEEKLY 2026-03-22] Volume improved"),
                entry("userContext", "- Profile: data not found"),
                entry("totalCalories", 62_000),
                entry("avgCalories", "2000.0"),
                entry("avgProtein", "150.0"),
                entry("avgFat", "70.0"),
                entry("avgCarbs", "215.0"),
                entry("daysTracked", 31),
                entry("nutritionText", "2026-03-01: 2000 kcal"),
                entry("totalSessions", 16),
                entry("totalVolumeKg", "52000.0"),
                entry("avgVolumePerSession", "3250.0"),
                entry("workoutText", "2026-03-01: 5000.0 kg")
        ));

        assertThat(prompt)
                .contains("name: monthly-report")
                .contains("version: v1")
                .contains("2026-03-01 - 2026-03-31")
                .contains("prefers morning workouts")
                .contains("Total sessions: 16")
                .doesNotContain("{{");
    }
}
