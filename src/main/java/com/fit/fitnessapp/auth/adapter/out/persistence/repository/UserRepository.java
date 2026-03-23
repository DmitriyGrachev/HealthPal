package com.fit.fitnessapp.auth.adapter.out.persistence.repository;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,User> {
    Optional<User> findByUsername(String username);

    Optional<User> findUserByUsernameAndEmail(String username, String email);
    Optional<User> findById(Long userId);
    boolean existsUserByUsername(String username);

    boolean existsUserByEmail(String email);
}
