package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fatsecret_connections")
@Getter
@Setter
public class FatSecretConnectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId; // Строгая изоляция: только ID, никаких ссылок на User.class!

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "access_token_secret", nullable = false)
    private String accessTokenSecret;
}