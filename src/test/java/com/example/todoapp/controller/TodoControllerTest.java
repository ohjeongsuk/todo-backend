package com.example.todoapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.Todo;
import com.example.todoapp.domain.TodoRepository;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.security.JwtTokenProvider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TodoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenFor(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
    }

    private User saveUser(String email) {
        return userRepository.save(
                User.createLocal(email, passwordEncoder.encode("test1234"), "닉네임"));
    }

    @Test
    void 생성_성공과_제목검증과_XSS정화() throws Exception {
        String token = tokenFor(saveUser("create@example.com"));

        mockMvc.perform(
                        post("/api/todos")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"title":"정상 제목","content":"<p>hi</p><script>alert(1)</script>","priority":"HIGH","dueDate":"2026-12-31"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("<p>hi</p>"));

        mockMvc.perform(
                        post("/api/todos")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"title":"","content":null,"priority":"LOW","dueDate":null}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        String tooLongTitle = "가".repeat(201);
        mockMvc.perform(
                        post("/api/todos")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        "{\"title\":\""
                                                + tooLongTitle
                                                + "\",\"content\":null,\"priority\":\"LOW\",\"dueDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void PUT은_완료상태를_보존하고_목록필터가_동작한다() throws Exception {
        User user = saveUser("update@example.com");
        String token = tokenFor(user);
        Todo todo =
                todoRepository.save(
                        Todo.create(
                                user,
                                "필터 테스트 제목",
                                null,
                                com.example.todoapp.domain.Priority.MEDIUM,
                                null));
        todo.complete();
        todoRepository.save(todo);

        mockMvc.perform(
                        put("/api/todos/" + todo.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"title":"수정된 제목","content":"<p>updated</p>","priority":"LOW","dueDate":"2027-01-01"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));

        mockMvc.perform(
                        get("/api/todos")
                                .header("Authorization", "Bearer " + token)
                                .param("completed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(
                        get("/api/todos")
                                .header("Authorization", "Bearer " + token)
                                .param("keyword", "수정된"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void toggle은_멱등하다() throws Exception {
        User user = saveUser("toggle@example.com");
        String token = tokenFor(user);
        Todo todo =
                todoRepository.save(
                        Todo.create(
                                user,
                                "토글 테스트",
                                null,
                                com.example.todoapp.domain.Priority.MEDIUM,
                                null));

        mockMvc.perform(
                        patch("/api/todos/" + todo.getId() + "/toggle")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        mockMvc.perform(
                        patch("/api/todos/" + todo.getId() + "/toggle")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    void 삭제하면_목록에서_제외되고_타인은_404() throws Exception {
        User owner = saveUser("owner@example.com");
        User other = saveUser("other@example.com");
        String ownerToken = tokenFor(owner);
        String otherToken = tokenFor(other);
        Todo todo =
                todoRepository.save(
                        Todo.create(
                                owner,
                                "삭제 테스트",
                                null,
                                com.example.todoapp.domain.Priority.MEDIUM,
                                null));

        mockMvc.perform(
                        get("/api/todos/" + todo.getId())
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        delete("/api/todos/" + todo.getId())
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/todos/" + todo.getId())
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
