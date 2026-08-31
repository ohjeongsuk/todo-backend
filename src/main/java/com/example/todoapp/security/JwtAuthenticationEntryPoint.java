package com.example.todoapp.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.example.todoapp.exception.ApiResponse;
import com.example.todoapp.exception.ErrorCode;

/**
 * 인증되지 않은 요청이 보호된 엔드포인트에 접근했을 때의 401 응답을 담당한다.
 *
 * <p>{@link com.example.todoapp.exception.GlobalExceptionHandler}는 Security 필터 체인 단계의
 * 예외를 잡지 못하므로, 여기서 별도로 동일한 {@link ApiResponse} 포맷을 만든다 (ROADMAP Phase 3).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.UNAUTHORIZED)));
    }
}
