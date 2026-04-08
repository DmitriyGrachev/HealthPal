package com.fit.fitnessapp.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;

@Entity
@Table(
        name = "ai_insights",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ai_insight_user_date_type", columnNames = {"user_id", "date", "insight_type"})
        }
)
@Getter
@Setter
public class AiInsightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Мапим Enum как строку (совпадает с твоим CHECK IN ('daily', ...))
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
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // База сама ставит дату, но хибернейту полезно знать ее в объекте
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}