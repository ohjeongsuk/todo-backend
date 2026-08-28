package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1 스캐폴딩 임시 설정.
 *
 * <p>Swagger UI 접속(Phase 1 DoD)만 열어두는 최소 구성이며, Phase 3에서 JWT 인증·CORS·경로별
 * 인가를 포함한 전체 SecurityFilterChain으로 교체된다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
