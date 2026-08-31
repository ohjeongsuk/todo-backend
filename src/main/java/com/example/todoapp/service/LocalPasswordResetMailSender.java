package com.example.todoapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 로컬 개발용. 메일 서버를 띄우지 않고 재설정 링크를 콘솔 로그로 출력한다 (CLAUDE.md 5장). */
@Component
@Profile("local")
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
