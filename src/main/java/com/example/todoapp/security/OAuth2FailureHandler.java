package com.example.todoapp.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.todoapp.service.CustomOAuth2UserService;

/**
 * 구글 로그인 실패 시 프론트엔드 로그인 화면으로 리다이렉트한다 (ROADMAP Phase 5). JSON 에러 응답을
 * 반환하지 않는다 — 브라우저 전체 페이지 이동으로 시작된 흐름이라 fetch로 받을 수 없다.
 *
 * <p>{@link CustomOAuth2UserService}가 계정 충돌로 던진 예외는 {@code error=email_conflict}로,
 * 그 외(사용자가 동의 화면에서 취소한 경우 등)는 {@code error=oauth_failed}로 구분한다 (PRD.md 7.2).
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final String frontendUrl;

    public OAuth2FailureHandler(@Value("${frontend.url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        String errorCode = resolveErrorCode(exception);
        String redirectUrl =
                UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                        .queryParam("error", errorCode)
                        .build()
                        .toUriString();
        response.sendRedirect(redirectUrl);
    }

    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception
                && CustomOAuth2UserService.EMAIL_CONFLICT_ERROR_CODE.equals(
                        oAuth2Exception.getError().getErrorCode())) {
            return CustomOAuth2UserService.EMAIL_CONFLICT_ERROR_CODE;
        }
        return "oauth_failed";
    }
}
