package com.example.todoapp.security;

import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Access Token(JWT) 생성·검증. {@code sub} 클레임에 사용자 id를 담는다 (CLAUDE.md 5장).
 *
 * <p>Refresh Token은 JWT가 아닌 불투명 문자열이므로 이 클래스가 다루지 않는다 ({@link
 * RefreshTokenService} 참조).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + accessTokenExpirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 토큰이 유효하면 사용자 id를, 만료·서명 오류 등으로 무효하면 빈 값을 반환한다. */
    public Optional<Long> parseUserId(String token) {
        try {
            String subject =
                    Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload()
                            .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
