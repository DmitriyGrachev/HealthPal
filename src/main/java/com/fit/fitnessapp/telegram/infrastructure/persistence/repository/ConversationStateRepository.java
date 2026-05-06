package com.fit.fitnessapp.telegram.infrastructure.persistence.repository;

import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.ConversationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationStateRepository extends JpaRepository<ConversationStateEntity, Long> {
}
