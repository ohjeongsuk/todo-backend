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
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.PasswordResetToken;
import com.example.todoapp.domain.PasswordResetTokenRepository;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

/**
 * 비밀번호 재설정 (PRD F-41 ~ F-45).
 *
 * <p><b>계정 존재 여부를 절대 노출하지 않는다</b> (PRD NF-31). 요청 처리는 "없는 계정", "구글 전용 계정",
 * "정상 계정" 세 경우 모두 조용히 끝나고 컨트롤러는 언제나 같은 응답을 낸다. 셋을 구분할 수 있는 신호(응답
 * 코드·본문·지연시간)를 만들지 않는다.
 *
 * <p>토큰 원문은 발급 시점에만 존재하고 DB에는 SHA-256 해시로만 저장한다 (PRD NF-27). 토큰 생성·해시
 * 방식은 {@link RefreshTokenService}와 같다 — 두 곳에서 쓰이지만 공용 유틸로 추출하지 않는 이유는, 서로
 * 다른 이유로 바뀔 수 있는 별개의 정책이기 때문이다.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailSender mailSender;
    private final PasswordResetRateLimiter rateLimiter;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final long expirationMinutes;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetMailSender mailSender,
            PasswordResetRateLimiter rateLimiter,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            @Value("${password-reset-token.expiration-minutes}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * 재설정 링크를 발송한다 (PRD F-41, F-43, F-45).
     *
     * <p>계정이 없거나 비밀번호가 없는 소셜 전용 계정이면 발송하지 않고 조용히 끝낸다. 호출자는 어느
     * 경우든 동일한 성공 응답을 반환해야 한다.
     *
     * @throws ApiException rate limit 초과 시 {@link ErrorCode#TOO_MANY_REQUESTS} (PRD NF-30).
     *     이 예외는 계정 존재 여부가 아니라 요청 빈도에만 의존하므로 NF-31에 위배되지 않는다.
     */
    @Transactional
    public void requestReset(String email, String clientIp) {
        String normalizedEmail = email.toLowerCase();

        if (rateLimiter.isLimitExceeded(normalizedEmail, clientIp)) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }

        Optional<User> found = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);
        if (found.isEmpty()) {
            return;
        }

        User user = found.get();
        // 구글 전용 계정은 재설정할 비밀번호 자체가 없다 (PRD F-45).
        if (!user.hasPassword()) {
            return;
        }

        // 새로 발급하면 기존 미사용 토큰을 무효화한다 (PRD F-43). 사용 처리로 무효화하는 이유는
        // 상태가 "미사용"과 "더는 못 씀" 둘뿐이라 별도 필드가 필요 없기 때문이다.
        List<PasswordResetToken> unused =
                passwordResetTokenRepository.findAllByUser_IdAndUsedAtIsNull(user.getId());
        unused.forEach(PasswordResetToken::use);

        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(expirationMinutes);
        passwordResetTokenRepository.save(
                PasswordResetToken.issue(user, hash(rawToken), expiresAt));

        mailSender.send(user.getEmail(), rawToken);
    }

    /**
     * 링크 진입 시 토큰이 아직 쓸 수 있는지 확인한다 (PRD F-42). 비밀번호 입력 화면을 보여주기 전에
     * 호출해, 만료된 링크로 폼을 채우게 하지 않는다.
     *
     * @throws ApiException 없거나 사용됐거나 만료된 토큰이면 {@link ErrorCode#RESET_TOKEN_INVALID}
     */
    @Transactional(readOnly = true)
    public void verify(String rawToken) {
        requireUsableToken(rawToken);
    }

    /**
     * 비밀번호를 교체하고 토큰을 소모한다 (PRD F-42, F-43, F-44).
     *
     * <p>비밀번호 변경·토큰 소모·세션 폐기는 <b>한 트랜잭션</b>에서 끝나야 한다. 중간에 끊기면 비밀번호는
     * 바뀌었는데 예전 세션이 살아 있는 상태가 남는다.
     *
     * <p>새 토큰을 발급하지 않는다 — 재설정 후 자동 로그인시키지 않고 로그인 화면으로 보낸다 (PRD F-44).
     */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = requireUsableToken(rawToken);
        User user = token.getUser();

        user.changePassword(passwordEncoder.encode(newPassword));
        token.use();
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private PasswordResetToken requireUsableToken(String rawToken) {
        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByTokenHash(hash(rawToken))
                        .orElseThrow(() -> new ApiException(ErrorCode.RESET_TOKEN_INVALID));

        if (token.isUsed() || token.isExpired()) {
            throw new ApiException(ErrorCode.RESET_TOKEN_INVALID);
        }
        return token;
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
