package com.example.todoapp.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.AuthProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.dto.LoginRequest;
import com.example.todoapp.dto.SignupRequest;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;
import com.example.todoapp.security.JwtTokenProvider;

/**
 * 회원가입·로그인·토큰 재발급·로그아웃을 담당한다. 로그인 실패는 원인(미가입 이메일 vs 비밀번호 오류 vs
 * 소셜 전용 계정)과 무관하게 동일한 {@link ErrorCode#UNAUTHORIZED}로 응답해 계정 존재 여부를 노출하지
 * 않는다 (PRD NF-31).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    public record LoginResult(String accessToken, String rawRefreshToken) {}

    @Transactional
    public LoginResult signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATED);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user =
                userRepository.save(User.createLocal(email, encodedPassword, request.nickname()));

        return issueTokens(user);
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user =
                userRepository
                        .findByEmailAndDeletedAtIsNull(email)
                        .filter(candidate -> candidate.getProvider() == AuthProvider.LOCAL)
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        // hasPassword()가 false(소셜 전용 계정)면 encode 비교가 자연히 실패해 동일한 401로 이어진다.
        if (!user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    /** Refresh Token 회전 성공 시 새 Access/Refresh 토큰 쌍을 반환한다. */
    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        if (!result.valid()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        String accessToken = jwtTokenProvider.createAccessToken(result.user().getId());
        return new LoginResult(accessToken, result.newRawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(Long userId) {
        return userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private LoginResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String rawRefreshToken = refreshTokenService.issue(user);
        return new LoginResult(accessToken, rawRefreshToken);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase();
    }
}
