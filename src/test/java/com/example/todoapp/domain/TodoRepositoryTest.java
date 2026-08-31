package com.example.todoapp.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TodoRepositoryTest {

    @Autowired private TodoRepository todoRepository;
    @Autowired private UserRepository userRepository;

    private User persistUser(String email) {
        return userRepository.save(User.createLocal(email, "encoded", "닉네임"));
    }

    @Test
    void createdAt이_자동으로_기록된다() {
        User user = persistUser("todo-owner1@example.com");
        Todo saved = todoRepository.save(Todo.create(user, "제목", "본문", Priority.MEDIUM, null));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createdAt이_UTC_기준으로_저장된다() {
        User user = persistUser("todo-owner2@example.com");
        Todo saved = todoRepository.save(Todo.create(user, "제목", "본문", Priority.MEDIUM, null));

        Instant createdAtUtc = saved.getCreatedAt().toInstant(java.time.ZoneOffset.UTC);
        assertThat(createdAtUtc)
                .isCloseTo(Instant.now(Clock.systemUTC()), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void 본인_할일만_id와_userId로_조회된다() {
        User owner = persistUser("owner@example.com");
        User stranger = persistUser("stranger@example.com");
        Todo todo = todoRepository.save(Todo.create(owner, "제목", "본문", Priority.MEDIUM, null));

        assertThat(todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(todo.getId(), owner.getId()))
                .isPresent();
        assertThat(
                        todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                                todo.getId(), stranger.getId()))
                .isEmpty();
    }

    @Test
    void 삭제된_할일은_목록_조회에서_제외된다() {
        User user = persistUser("owner2@example.com");
        Todo todo = todoRepository.save(Todo.create(user, "제목", "본문", Priority.MEDIUM, null));
        todo.softDelete();
        todoRepository.save(todo);

        var page =
                todoRepository.findAllByUser_IdAndDeletedAtIsNull(
                        user.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }
}
