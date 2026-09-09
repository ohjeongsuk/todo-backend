package com.example.todoapp.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.security.JwtAccessDeniedHandler;
import com.example.todoapp.security.JwtAuthenticationEntryPoint;
import com.example.todoapp.security.JwtAuthenticationFilter;
import com.example.todoapp.security.JwtTokenProvider;
import com.example.todoapp.security.OAuth2FailureHandler;
import com.example.todoapp.security.OAuth2SuccessHandler;
import com.example.todoapp.service.CustomOAuth2UserService;

/**
 * 인증·인가 전체 설정 (ROADMAP Phase 3, 구글 OAuth2는 Phase 5). Access Token은 JWT로 매 요청마다
 * {@link JwtAuthenticationFilter}가 검증하므로 세션을 쓰지 않는다({@code STATELESS}). CSRF는 쿠키 기반
 * 세션 인증이 아니므로 비활성화한다. {@link PasswordEncoderConfig}가 {@code PasswordEncoder} 빈을
 * 별도로 갖는 이유는 이 클래스 참고.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATHS = {
        // /swagger-ui.html은 SpringDoc이 /swagger-ui/index.html로 리다이렉트하는 진입점이라
        // /swagger-ui/** 패턴에 포함되지 않는다. 이것만 permitAll에서 빠지면 진입점 접근이
        // 401로 막혀 리다이렉트 자체가 실행되지 않는다.
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/api/auth/signup",
        "/api/auth/login",
        "/api/auth/refresh",
        // Access Token이 만료된 뒤에도 서버 세션(Refresh Token)을 정리할 수 있어야 한다.
        // 인증을 요구하면 만료된 사용자가 서버 로그아웃을 할 방법이 없어진다 (CLAUDE.md 5장).
        "/api/auth/logout",
        "/api/auth/password/**",
        "/oauth2/**",
        "/login/oauth2/**",
        // 첨부 업로드·조회는 JWT가 아니라 쿼리 서명 토큰으로 인가한다 (PRD NF-32).
        // 브라우저 <img> 태그는 Authorization 헤더를 실을 수 없고, S3 presigned PUT은
        // 그 헤더가 붙으면 서명 검증에 실패한다. 두 스토리지의 인증 형태를 같게 만들어야
        // 프론트엔드가 로컬/S3를 구분하지 않는다 (PRD F-50).
        // 인증이 사라진 게 아니라 AttachmentService가 StorageSignature로 직접 검증한다.
        "/api/attachments/*/upload",
        "/api/attachments/*/raw",
        // 헬스체크 (ROADMAP 11-2). 배포 스크립트와 외부 모니터링이 토큰 없이 호출해야 하므로 연다.
        // /actuator 전체가 아니라 health 하위만 연다는 점이 중요하다 — env·beans·configprops 등이
        // 열리면 환경변수와 빈 구성이 그대로 노출된다. 노출 자체도 application-prod.properties의
        // management.endpoints.web.exposure.include=health 로 이중으로 막아둔다.
        "/actuator/health",
        "/actuator/health/**",
        "/api/health",
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final String corsAllowedOrigin;

    public SecurityConfig(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            OAuth2FailureHandler oAuth2FailureHandler,
            @Value("${cors.allowed-origin}") String corsAllowedOrigin) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.oAuth2FailureHandler = oAuth2FailureHandler;
        this.corsAllowedOrigin = corsAllowedOrigin;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PERMIT_ALL_PATHS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(
                        oauth2 ->
                                oauth2.userInfoEndpoint(
                                                userInfo ->
                                                        userInfo.userService(
                                                                customOAuth2UserService))
                                        .successHandler(oAuth2SuccessHandler)
                                        .failureHandler(oAuth2FailureHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 명시적 origin만 허용한다. 쿠키를 주고받으므로 와일드카드(*)는 쓸 수 없다 (PRD NF-29).
        configuration.setAllowedOrigins(List.of(corsAllowedOrigin));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
