package com.example.todoapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.security.JwtTokenProvider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Test
    void 회원가입_성공과_중복이메일과_비밀번호바이트초과() throws Exception {
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"email":"signup@example.com","password":"test1234","nickname":"닉네임"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"email":"signup@example.com","password":"another1","nickname":"닉네임2"}
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"email":"longpw@example.com","password":"가나다라마바사아자차카타파하가나다라마바사아자차카","nickname":"닉네임"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인_성공과_실패시_동일문구() throws Exception {
        userRepository.save(
                User.createLocal("login@example.com", passwordEncoder.encode("correct1"), "닉네임"));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"email":"login@example.com","password":"correct1"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        var noUserResult =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                """
                                                {"email":"nobody@example.com","password":"whatever1"}
                                                """))
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        """
                                        {"email":"login@example.com","password":"wrongpass"}
                                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        result ->
                                org.assertj.core.api.Assertions.assertThat(
                                                result.getResponse().getContentAsString())
                                        .isEqualTo(noUserResult));
    }

    @Test
    void me_토큰없음401_정상200() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        User user =
                userRepository.save(
                        User.createLocal(
                                "me@example.com", passwordEncoder.encode("test1234"), "내닉네임"));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("내닉네임"))
                .andExpect(jsonPath("$.data.email").value("me@example.com"));
    }
}
