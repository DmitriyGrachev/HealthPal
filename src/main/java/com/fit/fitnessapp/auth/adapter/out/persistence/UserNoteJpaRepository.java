package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface UserNoteJpaRepository extends JpaRepository<UserNote, Long> {
    List<UserNote> findByUserIdOrderByRelatedDateDesc(Long userId);
    List<UserNote> findByUserIdAndRelatedDateBetweenOrderByRelatedDateDesc(Long userId, LocalDate from, LocalDate to);
}