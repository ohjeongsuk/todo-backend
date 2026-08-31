package com.example.todoapp.service;

/**
 * 비밀번호 재설정 링크 발송. 로컬/운영 프로파일별 구현이 다르다 (CLAUDE.md 5장) — 로컬은
 * {@link LocalPasswordResetMailSender}가 콘솔에 출력하고, 운영은 실제 SMTP 발송이 필요하다.
 */
public interface PasswordResetMailSender {

    void send(String email, String rawToken);
}
