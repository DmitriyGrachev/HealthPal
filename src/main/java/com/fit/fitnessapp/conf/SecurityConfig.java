package com.fit.fitnessapp.conf;

import com.fit.fitnessapp.security.TokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.http.HttpRequest;

//TODO подробно разобраться как работает Спринг Секюрити

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenFilter tokenFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Для JWT отключаем CSRF
                .cors(AbstractHttpConfigurer::disable) // Пока отключаем, на проде настроишь
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // STATELESS!
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/import/**").hasRole("VIP")
                        .requestMatchers("/fatsecret/callback").permitAll() // Callback оставляем открытым!
                        .requestMatchers("/ai/**").permitAll()
                        .requestMatchers("/auth/**").permitAll() // Вход и регистрация доступны всем
                        //.requestMatchers("/connect/**", "/callback/**").permitAll() // FatSecret OAuth endpoints
                        .anyRequest().authenticated() // Всё остальное - только с токеном
                )
                // Добавляем наш фильтр перед стандартным
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(); // Пароли храним ТОЛЬКО в хешах
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
            return config.getAuthenticationManager();
    }

}

