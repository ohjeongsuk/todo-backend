package com.example.todoapp.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;

/**
 * {@code Authorization: Bearer <token>} 헤더를 검사해 Access Token이 유효하면 인증 컨텍스트를
 * 채운다. 토큰이 없거나 무효하면 그대로 다음 필터로 넘긴다 — 인증이 필요한 엔드포인트의 차단은
 * {@link com.example.todoapp.config.SecurityConfig}의 인가 규칙이 담당한다.
 *
 * <p>{@code sub} 클레임으로 사용자를 조회할 때 {@code deleted_at IS NULL}을 함께 확인해, 탈퇴한
 * 사용자의 아직 만료되지 않은 Access Token으로는 인증되지 않게 한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtTokenProvider::parseUserId)
                .flatMap(userRepository::findByIdAndDeletedAtIsNull)
                .ifPresent(this::authenticate);

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(User user) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
