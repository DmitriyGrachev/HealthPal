package com.fit.fitnessapp.repository;

import com.fit.fitnessapp.model.fatsecret.UserFatsecretConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FatsecretConnectionRepository extends JpaRepository<UserFatsecretConnection, Long> {
    // Нам нужно искать соединение по ID пользователя
    Optional<UserFatsecretConnection> findByUserId(Long userId);
}