package com.fit.fitnessapp.auth.adapter.out.persistence.repository;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
    
    // Compatibility aliases
    default boolean existsUserByUsername(String username) { return existsByUsername(username); }
    default boolean existsUserByEmail(String email) { return existsByEmail(email); }

    @Query("SELECT f.userId FROM AuthFatSecretConnectionJpaEntity f")
    List<Long> findUserIdsWithFatSecretTokens();

    @Query("SELECT f.accessToken FROM AuthFatSecretConnectionJpaEntity f WHERE f.userId = :userId")
    String getFatSecretAccessTokenByUserId(@Param("userId") Long userId);

    @Query("SELECT f.accessTokenSecret FROM AuthFatSecretConnectionJpaEntity f WHERE f.userId = :userId")
    String getFatSecretAccessTokenSecretByUserId(@Param("userId") Long userId);
}
