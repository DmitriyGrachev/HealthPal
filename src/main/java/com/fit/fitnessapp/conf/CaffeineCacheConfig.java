package com.fit.fitnessapp.conf;

import com.fit.fitnessapp.model.fatsecret.FatSecretAuthState; // Убедись, что этот класс создан
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineCacheConfig {

    @Bean
    public Cache<String, FatSecretAuthState> requestTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES) // 10 минут на логин
                .maximumSize(1000)
                .build();
    }
}