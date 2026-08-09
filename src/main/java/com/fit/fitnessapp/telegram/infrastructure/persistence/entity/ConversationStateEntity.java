package com.fit.fitnessapp.telegram.infrastructure.persistence.entity;

import com.fit.fitnessapp.telegram.domain.ConversationState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "conversation_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationStateEntity {
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationState state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "updated_at")
    @org.hibernate.annotations.UpdateTimestamp
    private Instant updatedAt;

}
