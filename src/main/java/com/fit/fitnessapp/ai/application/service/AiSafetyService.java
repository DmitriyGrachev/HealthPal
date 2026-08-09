package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AiSafetyService {

    private static final int MAX_SUMMARY_LENGTH = 8_000;
    private static final int MAX_TELEGRAM_SUMMARY_LENGTH = 280;
    private static final Pattern BOUNDARY_CLOSING_TAG =
            Pattern.compile("(?i)</\\s*(user_question|user_note)\\s*>");

    private static final List<Pattern> MEDICAL_RED_FLAG_PATTERNS = List.of(
            Pattern.compile("chest pain", Pattern.CASE_INSENSITIVE),
            Pattern.compile("shortness of breath|difficulty breathing", Pattern.CASE_INSENSITIVE),
            Pattern.compile("stroke|heart attack|fainting|unconscious", Pattern.CASE_INSENSITIVE),
            Pattern.compile("severe dizziness|numbness", Pattern.CASE_INSENSITIVE),
            Pattern.compile("боль в груди|одышка|потеря сознания", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (all|previous) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt|override system", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now|disregard prior", Pattern.CASE_INSENSITIVE),
            Pattern.compile("игнорируй (все|предыдущие) инструкции", Pattern.CASE_INSENSITIVE)
    );

    public boolean hasMedicalRedFlags(String text) {
        if (text == null || text.isBlank()) return false;
        return MEDICAL_RED_FLAG_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    public boolean hasPromptInjection(String text) {
        if (text == null || text.isBlank()) return false;
        return PROMPT_INJECTION_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    public String sanitizeUserInput(String text) {
        if (text == null) return "";
        String sanitized = text.replace("```", "")
                .replace("<script>", "")
                .replace("</script>", "")
                .trim();
        return BOUNDARY_CLOSING_TAG.matcher(sanitized).replaceAll(match ->
                "&lt;/" + match.group(1).toLowerCase(Locale.ROOT) + "&gt;");
    }

    public String wrapUserBoundary(String tagName, String content) {
        String safeContent = sanitizeUserInput(content);
        return "<" + tagName + ">\n" + safeContent + "\n</" + tagName + ">";
    }

    public boolean isValidNutritionInsightResponse(NutritionInsightResponse response) {
        return response != null
                && hasTextWithin(response.summary(), MAX_SUMMARY_LENGTH)
                && (response.telegramSummary() == null
                || hasTextWithin(response.telegramSummary(), MAX_TELEGRAM_SUMMARY_LENGTH));
    }

    public boolean isValidNutritionInsightResponse(
            NutritionInsightResponse response,
            NutritionInsightResponse.ReportType expectedType,
            LocalDate expectedStart,
            LocalDate expectedEnd) {
        if (!isValidNutritionInsightResponse(response)
                || expectedType == null
                || expectedStart == null
                || expectedEnd == null
                || expectedStart.isAfter(expectedEnd)
                || response.reportType() != expectedType
                || response.periodCovered() == null
                || !expectedStart.equals(response.periodCovered().start())
                || !expectedEnd.equals(response.periodCovered().end())
                || !hasTextWithin(response.telegramSummary(), MAX_TELEGRAM_SUMMARY_LENGTH)
                || !validMacros(response.macroAnalysis())
                || !scoreInRange(response.goalAlignment())
                || !scoreInRange(response.confidenceScore())
                || response.anomalies() == null
                || response.recommendations() == null
                || response.followUpQuestions() == null
                || response.anomalies().size() > 10
                || response.recommendations().size() > 10
                || response.followUpQuestions().size() > 5) {
            return false;
        }

        boolean validAnomalies = response.anomalies().stream().allMatch(anomaly ->
                anomaly != null
                        && anomaly.date() != null
                        && !anomaly.date().isBefore(expectedStart)
                        && !anomaly.date().isAfter(expectedEnd)
                        && anomaly.type() != null
                        && anomaly.severity() != null
                        && hasTextWithin(anomaly.explanation(), 1_000));
        boolean validRecommendations = response.recommendations().stream().allMatch(item ->
                item != null
                        && item.priority() >= 1
                        && item.priority() <= 5
                        && item.category() != null
                        && hasTextWithin(item.action(), 1_000)
                        && hasTextWithin(item.rationale(), 1_000));
        boolean validQuestions = response.followUpQuestions().stream()
                .allMatch(question -> hasTextWithin(question, 500));
        return validAnomalies && validRecommendations && validQuestions;
    }

    private boolean validMacros(NutritionInsightResponse.MacroAnalysis macros) {
        return macros != null
                && finiteInRange(macros.avgCalories(), 0, 20_000)
                && finiteInRange(macros.avgProteinG(), 0, 2_000)
                && finiteInRange(macros.avgFatG(), 0, 2_000)
                && finiteInRange(macros.avgCarbsG(), 0, 2_000)
                && macros.calorieBalance() != null
                && macros.proteinAdequacy() != null;
    }

    private boolean finiteInRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    private boolean scoreInRange(float value) {
        return Float.isFinite(value) && value >= 0.0f && value <= 1.0f;
    }

    private boolean hasTextWithin(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    public String getMedicalSafetyMessage() {
        return "If you are experiencing acute medical symptoms such as chest pain or difficulty breathing, please consult a medical professional immediately. I am an AI assistant and cannot provide emergency medical diagnosis.";
    }
}
