package com.example.todoapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발·테스트용. 메일 서버를 띄우지 않고 재설정 링크를 콘솔 로그로 출력한다 (CLAUDE.md 5장).
 *
 * <p>{@code local}이 아니라 <b>{@code !prod}</b>로 지정한 이유: 테스트 프로파일에서도 이 빈이 있어야
 * {@link PasswordResetService}가 주입된다. {@code local}로 한정하면 {@code @SpringBootTest}가 컨텍스트
 * 로딩 단계에서 실패한다.
 *
 * <p>운영 프로파일에는 아직 구현체가 없다 — 실제 SMTP 발송은 ROADMAP Phase 11(11-1-1)로 이관했다.
 * 그때까지 {@code prod}로 기동하면 이 인터페이스의 빈이 없어 기동이 실패한다(의도된 방어).
 */
@Component
@Profile("!prod")
public class LocalPasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(LocalPasswordResetMailSender.class);

    private final String frontendUrl;

    public LocalPasswordResetMailSender(@Value("${frontend.url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void send(String email, String rawToken) {
        log.info("[비밀번호 재설정 링크] {} → {}/reset-password?token={}", email, frontendUrl, rawToken);
    }
}
