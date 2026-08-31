package com.example.todoapp.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;
import com.example.todoapp.service.AuthService;
import com.example.todoapp.service.AuthService.LoginResult;

/**
 * 구글 로그인 성공 시 JWT를 발급하고 프론트엔드 콜백으로 리다이렉트한다 (ROADMAP Phase 5). JSON 응답이
 * 아니라 302 리다이렉트인 이유는 브라우저의 전체 페이지 이동으로 시작된 OAuth2 흐름이라 fetch 응답을 받을
 * 수 없기 때문이다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            AuthService authService,
            UserRepository userRepository,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            @Value("${frontend.url}") String frontendUrl) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        String email = ((OAuth2User) authentication.getPrincipal()).getAttribute("email");
        User user =
                userRepository
                        .findByEmailAndDeletedAtIsNull(email)
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        LoginResult result = authService.issueTokensForOAuth2(user);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieFactory.create(result.rawRefreshToken()).toString());

        String redirectUrl =
                UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                        .queryParam("token", result.accessToken())
                        .build()
                        .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
