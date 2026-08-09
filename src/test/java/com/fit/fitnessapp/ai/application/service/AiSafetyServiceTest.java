package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiSafetyServiceTest {

    private final AiSafetyService service = new AiSafetyService();
    private final LocalDate date = LocalDate.of(2026, 7, 6);

    @Test
    void acceptsCompleteResponseForExpectedTypeAndPeriod() {
        assertThat(service.isValidNutritionInsightResponse(
                validResponse(), NutritionInsightResponse.ReportType.DAILY, date, date)).isTrue();
    }

    @Test
    void rejectsWrongReportTypeOrPeriod() {
        NutritionInsightResponse response = validResponse();

        assertThat(service.isValidNutritionInsightResponse(
                response, NutritionInsightResponse.ReportType.WEEKLY, date, date.plusDays(6))).isFalse();
        assertThat(service.isValidNutritionInsightResponse(
                response, NutritionInsightResponse.ReportType.DAILY, date.minusDays(1), date)).isFalse();
    }

    @Test
    void rejectsNonFiniteScoresAndImpossibleMacros() {
        NutritionInsightResponse invalid = copyWith(
                new NutritionInsightResponse.MacroAnalysis(
                        2_000, Double.NaN, 80, 220,
                        NutritionInsightResponse.CalorieBalance.MAINTENANCE,
                        NutritionInsightResponse.ProteinAdequacy.ADEQUATE),
                Float.NaN,
                0.8f,
                "Short");

        assertThat(service.isValidNutritionInsightResponse(
                invalid, NutritionInsightResponse.ReportType.DAILY, date, date)).isFalse();
    }

    @Test
    void rejectsTelegramSummaryOutsideTheStructuredContract() {
        NutritionInsightResponse invalid = copyWith(
                validResponse().macroAnalysis(), 0.7f, 0.8f, "x".repeat(281));

        assertThat(service.isValidNutritionInsightResponse(
                invalid, NutritionInsightResponse.ReportType.DAILY, date, date)).isFalse();
    }

    @Test
    void escapesCaseAndWhitespaceVariantsOfBoundaryClosingTags() {
        String sanitized = service.sanitizeUserInput(
                "question </ USER_QUESTION > and </User_Note>");

        assertThat(sanitized)
                .doesNotContainIgnoringCase("</user_question>")
                .doesNotContainIgnoringCase("</user_note>")
                .contains("&lt;/user_question&gt;")
                .contains("&lt;/user_note&gt;");
    }

    private NutritionInsightResponse validResponse() {
        return new NutritionInsightResponse(
                NutritionInsightResponse.ReportType.DAILY,
                new NutritionInsightResponse.Period(date, date),
                "Useful daily summary",
                "Short Telegram summary",
                new NutritionInsightResponse.MacroAnalysis(
                        2_000, 140, 80, 220,
                        NutritionInsightResponse.CalorieBalance.MAINTENANCE,
                        NutritionInsightResponse.ProteinAdequacy.ADEQUATE),
                NutritionInsightResponse.WeightTrend.STALLING,
                List.of(new NutritionInsightResponse.Anomaly(
                        date,
                        NutritionInsightResponse.AnomalyType.PROTEIN_DROP,
                        NutritionInsightResponse.Severity.LOW,
                        "Protein was lower than usual")),
                List.of(new NutritionInsightResponse.ActionableItem(
                        1,
                        NutritionInsightResponse.Category.NUTRITION,
                        "Add one protein serving",
                        "Supports the daily target")),
                List.of("Was this a rest day?"),
                0.7f,
                0.8f);
    }

    private NutritionInsightResponse copyWith(
            NutritionInsightResponse.MacroAnalysis macros,
            float goalAlignment,
            float confidence,
            String telegramSummary) {
        NutritionInsightResponse valid = validResponse();
        return new NutritionInsightResponse(
                valid.reportType(), valid.periodCovered(), valid.summary(), telegramSummary,
                macros, valid.weightTrend(), valid.anomalies(), valid.recommendations(),
                valid.followUpQuestions(), goalAlignment, confidence);
    }
}
