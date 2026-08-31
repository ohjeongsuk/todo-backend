package com.example.todoapp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키 생성. {@code httpOnly} + {@code Path=/api/auth}로 제한해 일반 API 요청에는
 * 실리지 않게 한다 (PRD NF-28). {@code SameSite}·{@code Secure} 값은 프로파일별로 다르다 — 로컬은
 * {@code Lax}(same-site), 운영은 Amplify↔EC2가 cross-site이므로 {@code None; Secure} (CLAUDE.md 5장).
 */
@Component
public class RefreshTokenCookieFactory {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final long expirationDays;
    private final String sameSite;
    private final boolean secure;

    public RefreshTokenCookieFactory(
            @Value("${refresh-token.expiration-days}") long expirationDays,
            @Value("${refresh-token-cookie.same-site}") String sameSite,
            @Value("${refresh-token-cookie.secure}") boolean secure) {
        this.expirationDays = expirationDays;
        this.sameSite = sameSite;
        this.secure = secure;
    }

    public ResponseCookie create(String rawToken) {
        return baseCookie(rawToken).maxAge(java.time.Duration.ofDays(expirationDays)).build();
    }

    /** 로그아웃 시 클라이언트 쿠키를 즉시 만료시킨다. */
    public ResponseCookie expire() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH);
    }
}
