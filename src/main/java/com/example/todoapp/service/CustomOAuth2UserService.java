package com.example.todoapp.service;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.AuthProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;

/**
 * 구글 OAuth2 로그인 시 사용자 조회·생성을 담당한다 (ROADMAP Phase 5).
 *
 * <p>동일 이메일의 로컬 계정이 이미 있으면 자동 연동하지 않고 거부한다 (CLAUDE.md 5장). 이 경우 {@link
 * #EMAIL_CONFLICT_ERROR_CODE}를 실은 {@link OAuth2AuthenticationException}을 던지고, {@link
 * com.example.todoapp.security.OAuth2FailureHandler}가 이를 구분해 {@code email_conflict}로
 * 리다이렉트한다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    public static final String EMAIL_CONFLICT_ERROR_CODE = "email_conflict";

    private static final String NAME_ATTRIBUTE_KEY = "sub";
    private static final int NICKNAME_MAX_LENGTH = 50;

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        resolveUser(oAuth2User.getAttributes());
        // 이 OAuth2User는 SecurityContext에만 임시로 실리고, 실제 인증은 OAuth2SuccessHandler가
        // JWT를 직접 발급하는 방식이므로(JwtAuthenticationFilter와 동일하게 User를 principal로
        // 쓰지 않음) 권한은 실질적 의미가 없다. DefaultOAuth2User 생성자 계약상 최소 1개가 필요하다.
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                oAuth2User.getAttributes(),
                NAME_ATTRIBUTE_KEY);
    }

    /**
     * 구글 프로필로 사용자를 조회하거나 생성한다. 패키지 전용으로 열어 {@code loadUser}가 거치는
     * {@code super.loadUser()}(실제 구글 서버 호출) 없이 단위 테스트에서 직접 호출한다 (ROADMAP Phase
     * 5 — OAuth2 흐름은 MockMvc로 끝까지 검증할 수 없어 이 메서드만 단위 테스트 대상으로 삼는다).
     */
    User resolveUser(Map<String, Object> attributes) {
        String email = normalizeEmail((String) attributes.get("email"));
        String name = (String) attributes.get("name");

        return userRepository
                .findByEmailAndDeletedAtIsNull(email)
                .map(this::requireGoogleProvider)
                .orElseGet(
                        () ->
                                userRepository.save(
                                        User.createGoogle(email, resolveNickname(name, email))));
    }

    private User requireGoogleProvider(User existing) {
        if (existing.getProvider() != AuthProvider.GOOGLE) {
            OAuth2Error error =
                    new OAuth2Error(EMAIL_CONFLICT_ERROR_CODE, "이미 로컬 계정으로 가입된 이메일입니다.", null);
            throw new OAuth2AuthenticationException(error, error.getDescription());
        }
        return existing;
    }

    /** 닉네임은 구글 name → 없으면 이메일 @ 앞부분 → 50자 초과 시 절삭 (ROADMAP Phase 5). */
    private String resolveNickname(String name, String email) {
        String candidate =
                (name != null && !name.isBlank()) ? name : email.substring(0, email.indexOf('@'));
        return candidate.length() > NICKNAME_MAX_LENGTH
                ? candidate.substring(0, NICKNAME_MAX_LENGTH)
                : candidate;
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase();
    }
}
