package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;

public interface UserNoteJpaRepository extends JpaRepository<UserNote, Long> {
    List<UserNote> findByUserIdOrderByRelatedDateDesc(Long userId);
    List<UserNote> findByUserIdAndRelatedDateBetweenOrderByRelatedDateDesc(Long userId, LocalDate from, LocalDate to);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select note from UserNote note where note.id = :id and note.userId = :userId")
    java.util.Optional<UserNote> findOwnedByIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId);
    void deleteByUserIdAndId(Long userId, Long id);
}
