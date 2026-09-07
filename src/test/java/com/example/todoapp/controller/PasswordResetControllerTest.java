package com.example.todoapp.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.PasswordResetToken;
import com.example.todoapp.domain.PasswordResetTokenRepository;
import com.example.todoapp.domain.RefreshTokenRepository;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.service.RefreshTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 13번 — 비밀번호 재설정 (PRD F-41 ~ F-45, NF-30, NF-31).
 *
 * <p><b>테스트마다 서로 다른 클라이언트 IP를 쓴다.</b> rate limiter가 싱글턴 빈이고 상태를 메모리에
 * 들고 있어, 모든 요청이 같은 IP(MockMvc 기본값 127.0.0.1)로 나가면 클래스 안의 네 번째 요청부터
 * 429가 나 테스트끼리 간섭한다. {@code @Transactional} 롤백은 DB만 되돌리지 rate limiter는 못 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void 없는계정과_소셜전용계정도_정상계정과_동일하게_응답한다() throws Exception {
        userRepository.save(User.createGoogle("social@example.com", "구글유저"));

        // 없는 계정 (PRD NF-31)
        forgot("nobody@example.com", "10.0.0.1").andExpect(status().isNoContent());
        // 소셜 전용 계정 — 응답은 같지만 토큰은 발급되지 않는다 (PRD F-45)
        forgot("social@example.com", "10.0.0.2").andExpect(status().isNoContent());

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void 재설정_요청시_토큰이_발급되고_기존_미사용토큰은_무효화된다() throws Exception {
        User user = saveLocalUser("reissue@example.com", "before01");
        passwordResetTokenRepository.save(
                PasswordResetToken.issue(
                        user,
                        hash("old-token"),
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)));

        forgot("reissue@example.com", "10.0.0.3").andExpect(status().isNoContent());

        // 기존 토큰은 사용 처리되고 새 토큰만 남는다 (PRD F-43)
        assertThat(passwordResetTokenRepository.findAllByUser_IdAndUsedAtIsNull(user.getId()))
                .hasSize(1);
        assertThat(passwordResetTokenRepository.findByTokenHash(hash("old-token")))
                .get()
                .satisfies(token -> assertThat(token.isUsed()).isTrue());
    }

    @Test
    void 유효한_토큰만_verify를_통과한다() throws Exception {
        User user = saveLocalUser("verify@example.com", "before01");
        issueToken(user, "valid-token", 30);
        issueToken(user, "expired-token", -1);
        PasswordResetToken used = issueToken(user, "used-token", 30);
        used.use();
        passwordResetTokenRepository.save(used);

        mockMvc.perform(get("/api/auth/password/verify").param("token", "valid-token"))
                .andExpect(status().isNoContent());

        // 만료·사용됨·존재하지 않는 토큰은 모두 같은 코드로 거절한다 (PRD F-42, F-43)
        for (String invalid : new String[] {"expired-token", "used-token", "forged-token"}) {
            mockMvc.perform(get("/api/auth/password/verify").param("token", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
        }
    }

    @Test
    void 재설정_성공시_비밀번호가_바뀌고_토큰이_소모되며_모든세션이_폐기된다() throws Exception {
        User user = saveLocalUser("reset@example.com", "before01");
        issueToken(user, "reset-token", 30);
        refreshTokenService.issue(user);
        refreshTokenService.issue(user);

        mockMvc.perform(
                        post("/api/auth/password/reset")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"token":"reset-token","newPassword":"after001"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("after001", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("before01", updated.getPassword())).isFalse();

        // 1회용 (PRD F-43)
        assertThat(passwordResetTokenRepository.findByTokenHash(hash("reset-token")))
                .get()
                .satisfies(token -> assertThat(token.isUsed()).isTrue());

        // 전체 세션 폐기 (PRD F-44)
        assertThat(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId()))
                .isEmpty();
    }

    @Test
    void 사용한_링크는_두번째부터_거절된다() throws Exception {
        User user = saveLocalUser("once@example.com", "before01");
        issueToken(user, "once-token", 30);

        String body =
                """
                {"token":"once-token","newPassword":"after001"}
                """;
        mockMvc.perform(
                        post("/api/auth/password/reset")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/auth/password/reset")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 같은_IP에서_10분에_세번을_넘기면_429로_막힌다() throws Exception {
        saveLocalUser("ratelimit@example.com", "before01");

        for (int i = 0; i < 3; i++) {
            forgot("ratelimit@example.com", "10.0.0.9").andExpect(status().isNoContent());
        }
        // 네 번째 (PRD NF-30)
        forgot("ratelimit@example.com", "10.0.0.9")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String email, String ip)
            throws Exception {
        return mockMvc.perform(
                post("/api/auth/password/forgot")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}")
                        .with(fromIp(ip)));
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private User saveLocalUser(String email, String rawPassword) {
        return userRepository.save(
                User.createLocal(email, passwordEncoder.encode(rawPassword), "닉네임"));
    }

    private PasswordResetToken issueToken(User user, String rawToken, long minutesFromNow) {
        return passwordResetTokenRepository.save(
                PasswordResetToken.issue(
                        user,
                        hash(rawToken),
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(minutesFromNow)));
    }

    /** 서비스와 같은 방식으로 해시한다 — 원문은 DB에 없으므로 테스트가 직접 해시해 심는다. */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
