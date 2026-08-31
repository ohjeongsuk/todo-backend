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

/** 회원가입·로그인·토큰 재발급·내 정보 조회 API. 로그아웃 API는 만들지 않는다(AUTH-06은 Phase 7에서 프론트 전용 처리). */
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
