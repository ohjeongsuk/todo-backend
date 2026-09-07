package com.example.todoapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp.dto.PasswordResetConfirmRequest;
import com.example.todoapp.dto.PasswordResetRequest;
import com.example.todoapp.exception.ApiResponse;
import com.example.todoapp.service.PasswordResetService;

/**
 * 비밀번호 재설정 API (PRD F-41 ~ F-45, 7.5).
 *
 * <p>세 경로 모두 인증 없이 호출된다. {@code SecurityConfig}의 {@code PERMIT_ALL_PATHS}에
 * {@code /api/auth/password/**}가 이미 포함돼 있다.
 *
 * <p>{@code AuthController}와 분리한 이유는 이 흐름이 로그인 세션과 무관하고(토큰을 발급하지도 받지도
 * 않는다) 수명주기도 다르기 때문이다.
 */
@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * 재설정 링크 발송 요청.
     *
     * <p><b>계정이 있든 없든, 소셜 전용이든 언제나 204를 낸다</b> (PRD F-41, NF-31). 프론트는 이 응답만
     * 보고 "메일을 보냈습니다"를 표시하며, 실제로 보냈는지는 알 수 없다 — 그게 의도다.
     */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        passwordResetService.requestReset(request.email(), clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /** 링크 진입 시 토큰 유효성 선확인 (PRD F-42). 유효하면 204, 아니면 400 RESET_TOKEN_INVALID. */
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        passwordResetService.verify(token);
        return ResponseEntity.noContent().build();
    }

    /** 새 비밀번호 확정. 성공해도 토큰을 발급하지 않는다 — 자동 로그인시키지 않는다 (PRD F-44). */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> reset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * rate limit 집계용 클라이언트 IP (PRD NF-30).
     *
     * <p>운영에서는 nginx 리버스 프록시 뒤에 있어(ROADMAP 11-3) {@code getRemoteAddr()}가 프록시 IP로
     * 고정된다. {@code X-Forwarded-For}의 첫 값을 우선 쓰되, 이 헤더는 클라이언트가 위조할 수 있으므로
     * <b>인가 판단에는 절대 쓰지 않는다.</b> 빈도 집계 용도로만 쓴다.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
