package com.fit.fitnessapp.auth.adapter.out.persistence.repository;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, User> {
    Optional<User> findByUsername(String username);

    Optional<User> findUserByUsernameAndEmail(String username, String email);
    Optional<User> findById(Long userId);
    boolean existsUserByUsername(String username);

    boolean existsUserByEmail(String email);

    @Query("SELECT f.userId FROM FatSecretConnectionJpaEntity f")
    List<Long> findUserIdsWithFatSecretTokens();

    @Query("SELECT f.accessToken FROM FatSecretConnectionJpaEntity f WHERE f.userId = :userId")
    String getFatSecretAccessTokenByUserId(@Param("userId") Long userId);

    @Query("SELECT f.accessTokenSecret FROM FatSecretConnectionJpaEntity f WHERE f.userId = :userId")
    String getFatSecretAccessTokenSecretByUserId(@Param("userId") Long userId);
}
