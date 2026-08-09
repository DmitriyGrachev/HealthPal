package com.fit.fitnessapp.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppTimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
