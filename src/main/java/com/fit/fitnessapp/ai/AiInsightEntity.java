package com.fit.fitnessapp.ai;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import com.fit.fitnessapp.ai.api.InsightType;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;

@Entity
@Table(
        name = "ai_insights",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_insight_user_date_type", columnNames = {"user_id", "date", "insight_type"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_type", nullable = false, length = 20)
    private InsightType insightType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "insight_text", columnDefinition = "TEXT")
    private String insightText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "insight_data", columnDefinition = "jsonb")
    private Map<String, Object> insightData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_response", columnDefinition = "jsonb")
    private NutritionInsightResponse structuredResponse;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private Integer schemaVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}