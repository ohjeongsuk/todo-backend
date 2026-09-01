package com.example.todoapp.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp.domain.User;
import com.example.todoapp.dto.LoginRequest;
import com.example.todoapp.dto.SignupRequest;
import com.example.todoapp.dto.TokenResponse;
import com.example.todoapp.dto.UserResponse;
import com.example.todoapp.exception.ApiResponse;
import com.example.todoapp.security.RefreshTokenCookieFactory;
import com.example.todoapp.service.AuthService;
import com.example.todoapp.service.AuthService.LoginResult;

/** 회원가입·로그인·로그아웃·토큰 재발급·내 정보 조회 API. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public AuthController(
            AuthService authService, RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return tokenResponse(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return tokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue("refresh_token") String rawRefreshToken) {
        return tokenResponse(authService.refresh(rawRefreshToken));
    }

    /**
     * 로그아웃. Refresh Token을 DB에서 폐기하고 쿠키를 만료시킨다 (CLAUDE.md 5장, PRD F-08·7.4).
     *
     * <p>쿠키를 {@code required = false}로 받는 이유: 로그아웃은 멱등이어야 한다. 쿠키가 없다는 것은
     * 이미 로그아웃된 상태이지 오류가 아니므로 400·401을 내지 않는다. 쿠키 만료 헤더는 어느 경우든 붙인다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refresh_token", required = false) String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            authService.logout(rawRefreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.expire().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal User principal) {
        User user = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(LoginResult result) {
        ResponseCookie cookie = refreshTokenCookieFactory.create(result.rawRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(new TokenResponse(result.accessToken())));
    }
}
