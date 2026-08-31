package com.example.todoapp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.RefreshToken;
import com.example.todoapp.domain.RefreshTokenRepository;
import com.example.todoapp.domain.User;

/**
 * Refresh Token 발급·검증·회전을 담당한다. 토큰 원문은 발급 시점에만 존재하고 DB에는 SHA-256 해시로만
 * 저장한다 (CLAUDE.md 5장, PRD NF-27).
 *
 * <p>이미 폐기된 토큰이 재사용되면 탈취로 간주해 해당 사용자의 모든 Refresh Token을 폐기한다 (PRD F-39).
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${refresh-token.expiration-days}") long expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationDays = expirationDays;
    }

    /** 새 Refresh Token을 발급하고 원문을 반환한다 (호출자가 쿠키에 담는다). */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(expirationDays);
        refreshTokenRepository.save(RefreshToken.issue(user, hash(rawToken), expiresAt));
        return rawToken;
    }

    /** 결과. {@code valid=false}면 재사용 감지 등으로 세션이 전부 폐기됐다는 뜻이다. */
    public record RotationResult(boolean valid, String newRawToken, User user) {}

    /**
     * 제시된 Refresh Token을 검증하고 회전(폐기 + 재발급)한다.
     *
     * <ul>
     *   <li>존재하지 않거나 만료됨 → {@code invalid}
     *   <li>이미 폐기된 토큰(재사용) → 해당 사용자의 모든 토큰을 폐기하고 {@code invalid}
     *   <li>정상 → 기존 토큰 폐기 + 새 토큰 발급
     * </ul>
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        String tokenHash = hash(rawToken);
        var found = refreshTokenRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return new RotationResult(false, null, null);
        }

        RefreshToken token = found.get();
        if (token.isRevoked()) {
            revokeAllForUser(token.getUser().getId());
            return new RotationResult(false, null, null);
        }
        if (token.isExpired()) {
            return new RotationResult(false, null, null);
        }

        token.revoke();
        String newRawToken = issue(token.getUser());
        return new RotationResult(true, newRawToken, token.getUser());
    }

    /** 로그아웃 시 제시된 Refresh Token 하나만 폐기한다. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    /** 비밀번호 재설정 등으로 사용자의 모든 세션을 강제 종료할 때 사용한다 (PRD F-44). */
    @Transactional
    public void revokeAllForUser(Long userId) {
        List<RefreshToken> tokens =
                refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId);
        tokens.forEach(RefreshToken::revoke);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
