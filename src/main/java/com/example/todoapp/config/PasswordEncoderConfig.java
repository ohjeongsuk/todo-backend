package com.example.todoapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link PasswordEncoder} 빈을 {@link SecurityConfig}에서 분리한다. {@code AuthService}가
 * {@code PasswordEncoder}를 생성자로 주입받는데, {@code SecurityConfig}가 OAuth2 핸들러를 거쳐
 * {@code AuthService}에 의존하게 되면서 이 빈이 {@code SecurityConfig} 안에 있으면 자기 자신을
 * 향하는 순환 의존성이 생긴다 (ROADMAP Phase 5).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
