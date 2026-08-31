package com.example.todoapp.service;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.example.todoapp.domain.AuthProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomOAuth2UserService}의 사용자 조회·생성 로직만 검증하는 단위 테스트다 (ROADMAP Phase 5).
 * OAuth2 흐름 전체(구글 서버와의 실제 통신)는 MockMvc로 끝까지 검증할 수 없으므로, {@code
 * super.loadUser()}를 거치지 않는 {@link CustomOAuth2UserService#resolveUser}만 대상으로 한다
 * (CLAUDE.md 14장).
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void 신규_이메일이면_구글_계정을_생성한다() {
        Map<String, Object> attributes = Map.of("email", "new@example.com", "name", "새사용자");
        when(userRepository.findByEmailAndDeletedAtIsNull("new@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = customOAuth2UserService.resolveUser(attributes);

        assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getNickname()).isEqualTo("새사용자");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 기존_구글계정_재로그인시_새로_생성하지_않는다() {
        User existing = User.createGoogle("existing@example.com", "기존사용자");
        Map<String, Object> attributes = Map.of("email", "existing@example.com", "name", "기존사용자");
        when(userRepository.findByEmailAndDeletedAtIsNull("existing@example.com"))
                .thenReturn(Optional.of(existing));

        User result = customOAuth2UserService.resolveUser(attributes);

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 동일_이메일_로컬계정이_있으면_거부한다() {
        User localUser = User.createLocal("conflict@example.com", "encoded-password", "로컬사용자");
        Map<String, Object> attributes = Map.of("email", "conflict@example.com", "name", "로컬사용자");
        when(userRepository.findByEmailAndDeletedAtIsNull("conflict@example.com"))
                .thenReturn(Optional.of(localUser));

        assertThatThrownBy(() -> customOAuth2UserService.resolveUser(attributes))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(
                        ex ->
                                assertThat(
                                                ((OAuth2AuthenticationException) ex)
                                                        .getError()
                                                        .getErrorCode())
                                        .isEqualTo(
                                                CustomOAuth2UserService.EMAIL_CONFLICT_ERROR_CODE));
        verify(userRepository, never()).save(any(User.class));
    }
}
