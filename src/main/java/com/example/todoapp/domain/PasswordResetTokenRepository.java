package com.example.todoapp.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 새 토큰 발급 전 기존 미사용 토큰을 찾아 무효화하기 위한 조회 (PRD F-43). */
    List<PasswordResetToken> findAllByUser_IdAndUsedAtIsNull(Long userId);
}
