package com.example.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;

    @Test
    void createdAt이_자동으로_기록된다() {
        User saved = userRepository.save(User.createLocal("test@example.com", "encoded", "닉네임"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createdAt이_UTC_기준으로_저장된다() {
        User saved = userRepository.save(User.createLocal("utc@example.com", "encoded", "닉네임"));

        Instant createdAtUtc = saved.getCreatedAt().toInstant(java.time.ZoneOffset.UTC);
        assertThat(createdAtUtc)
                .isCloseTo(Instant.now(Clock.systemUTC()), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void 이메일로_삭제되지_않은_사용자를_조회한다() {
        userRepository.save(User.createLocal("find@example.com", "encoded", "닉네임"));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("find@example.com")).isPresent();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("nobody@example.com")).isEmpty();
    }

    @Test
    void 삭제된_사용자는_이메일_조회에서_제외된다() {
        User user = userRepository.save(User.createLocal("deleted@example.com", "encoded", "닉네임"));
        user.softDelete();
        userRepository.save(user);

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("deleted@example.com")).isEmpty();
        assertThat(userRepository.existsByEmailAndDeletedAtIsNull("deleted@example.com")).isFalse();
    }
}
