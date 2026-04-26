package com.fit.fitnessapp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fit.fitnessapp.sharedai.NutritionInsightResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

class InsightSerializationTest {

    @Test
    void shouldSerializeAndDeserializeNutritionInsightResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule());

        NutritionInsightResponse response = new NutritionInsightResponse(
                NutritionInsightResponse.ReportType.DAILY,
                new NutritionInsightResponse.Period(LocalDate.now(), LocalDate.now()),
                "Daily summary",
                "Daily telegram summary",
                new NutritionInsightResponse.MacroAnalysis(
                        2000.0, 150.0, 70.0, 200.0,
                        NutritionInsightResponse.CalorieBalance.MAINTENANCE,
                        NutritionInsightResponse.ProteinAdequacy.ADEQUATE
                ),
                NutritionInsightResponse.WeightTrend.STALLING,
                List.of(new NutritionInsightResponse.Anomaly(
                        LocalDate.now(),
                        NutritionInsightResponse.AnomalyType.CALORIE_SPIKE,
                        NutritionInsightResponse.Severity.MEDIUM,
                        "Too many snacks"
                )),
                List.of(new NutritionInsightResponse.ActionableItem(
                        1,
                        NutritionInsightResponse.Category.NUTRITION,
                        "Eat more fiber",
                        "Digestion health"
                )),
                List.of("Did you sleep well?"),
                0.85f,
                0.95f
        );

        String json = mapper.writeValueAsString(response);
        NutritionInsightResponse deserialized = mapper.readValue(json, NutritionInsightResponse.class);

        assertThat(deserialized.summary()).isEqualTo(response.summary());
        assertThat(deserialized.reportType()).isEqualTo(response.reportType());
        assertThat(deserialized.macroAnalysis().avgCalories()).isEqualTo(response.macroAnalysis().avgCalories());
        assertThat(deserialized.anomalies()).hasSize(1);
        assertThat(deserialized.anomalies().get(0).type()).isEqualTo(NutritionInsightResponse.AnomalyType.CALORIE_SPIKE);
        assertThat(deserialized.followUpQuestions()).containsExactly("Did you sleep well?");
    }
}
