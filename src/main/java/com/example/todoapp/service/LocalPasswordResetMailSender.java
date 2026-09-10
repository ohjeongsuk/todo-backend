package com.example.todoapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 메일 서버를 띄우지 않고 재설정 링크를 콘솔 로그로 출력한다 (CLAUDE.md 5장).
 *
 * <p><b>[임시] 프로파일 제한을 걸지 않은 이유 (ROADMAP 11-2 배포 결정).</b> 원래는 {@code @Profile("!prod")}
 * 였다. 운영용 SMTP 구현체가 아직 없어 {@code prod}로 기동하면 {@link PasswordResetMailSender} 빈이 하나도
 * 없고, 그러면 {@link PasswordResetService}의 생성자 주입이 실패해 <b>운영 프로파일 기동 자체가 불가능</b>했다.
 * EC2 배포를 먼저 진행하기 위해 제한을 임시로 풀어 운영에서도 이 구현체가 뜨게 했다.
 *
 * <p><b>되돌릴 시점:</b> ROADMAP 11-1-1의 SMTP/SES 구현체({@code @Profile("prod")})가 들어오면
 * 이 클래스에 {@code @Profile("!prod")}를 다시 붙인다.
 *
 * <p><b>감수 중인 리스크:</b> 운영에서는 재설정 링크가 서버 로그(journald)에 평문으로 남는다. 즉
 * <b>서버 로그 열람 권한이 곧 임의 계정의 비밀번호 재설정 권한</b>이 된다. 실사용자에게 공개하기 전에
 * 반드시 해소해야 한다. 그래서 운영 프로파일에서는 아래 생성자가 기동 시 WARN 로그를 남긴다.
 */
@Component
public class LocalPasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(LocalPasswordResetMailSender.class);

    private final String frontendUrl;

    public LocalPasswordResetMailSender(
            @Value("${frontend.url}") String frontendUrl, Environment environment) {
        this.frontendUrl = frontendUrl;
        // 운영에서 이 구현체가 뜬 것은 임시 상태다. 기동 로그에서 바로 눈에 띄도록 경고를 남긴다.
        if (environment.matchesProfiles("prod")) {
            log.warn(
                    "[임시] 운영 프로파일에서 콘솔 로그 방식 메일 발송기가 활성화되었다. "
                            + "비밀번호 재설정 링크가 서버 로그에 평문으로 기록된다. "
                            + "실사용자 공개 전에 SMTP 구현체로 교체할 것 (ROADMAP 11-1-1).");
        }
    }

    @Override
    public void send(String email, String rawToken) {
        log.info("[비밀번호 재설정 링크] {} → {}/reset-password?token={}", email, frontendUrl, rawToken);
    }
}
