package com.fit.fitnessapp.ai.domain.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Typed contract for AI-generated nutrition insights.
 * Used by Spring AI's BeanOutputConverter to enforce structured responses.
 */
public record NutritionInsightResponse(
    ReportType reportType,
    Period periodCovered,
    
    // Structured Narratives
    String summary,           // Human-readable overview
    String telegramSummary,   // Max 280 chars, pre-formatted for Telegram Markdown
    
    // Core Metrics
    MacroAnalysis macroAnalysis,
    WeightTrend weightTrend,
    
    // Agentic Seeds (Phase 1/2 Readiness)
    List<Anomaly> anomalies,
    List<ActionableItem> recommendations,
    List<String> followUpQuestions, // Bridge to proactive questioning
    
    // Analysis Metadata
    float goalAlignment,      // 0.0 - 1.0 score
    float confidenceScore     // 0.0 - 1.0 score for MoE routing logic
) {
    public enum ReportType { DAILY, WEEKLY, MONTHLY }
    
    public record Period(LocalDate start, LocalDate end) {}
    
    public record MacroAnalysis(
        double avgCalories,
        double avgProteinG,
        double avgFatG,
        double avgCarbsG,
        CalorieBalance calorieBalance,
        ProteinAdequacy proteinAdequacy
    ) {}
    
    public enum CalorieBalance { SURPLUS, DEFICIT, MAINTENANCE }
    
    public enum ProteinAdequacy { ADEQUATE, LOW, HIGH }
    
    public record Anomaly(
        LocalDate date,
        AnomalyType type,
        Severity severity,
        String explanation
    ) {}
    
    public enum AnomalyType { CALORIE_SPIKE, PROTEIN_DROP, WEIGHT_STALL, UNKNOWN }
    
    public enum Severity { LOW, MEDIUM, HIGH }
    
    public record ActionableItem(
        int priority, // 1 (Highest) to 5
        Category category,
        String action,
        String rationale
    ) {}
    
    public enum Category { NUTRITION, WORKOUT, RECOVERY, HYDRATION }
    
    public enum WeightTrend { LOSING, GAINING, STALLING, FLUCTUATING }
}
