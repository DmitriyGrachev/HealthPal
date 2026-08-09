package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AiSafetyService {

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
        return text.replace("```", "")
                .replace("<script>", "")
                .replace("</script>", "")
                .replace("</user_question>", "&lt;/user_question&gt;")
                .replace("</user_note>", "&lt;/user_note&gt;")
                .trim();
    }

    public String wrapUserBoundary(String tagName, String content) {
        String safeContent = sanitizeUserInput(content);
        return "<" + tagName + ">\n" + safeContent + "\n</" + tagName + ">";
    }

    public boolean isValidNutritionInsightResponse(NutritionInsightResponse response) {
        if (response == null) return false;
        if (response.summary() == null || response.summary().isBlank()) return false;
        if (response.macroAnalysis() != null) {
            double cals = response.macroAnalysis().avgCalories();
            if (cals < 0 || cals > 20000) return false;
        }
        return true;
    }

    public String getMedicalSafetyMessage() {
        return "If you are experiencing acute medical symptoms such as chest pain or difficulty breathing, please consult a medical professional immediately. I am an AI assistant and cannot provide emergency medical diagnosis.";
    }
}
