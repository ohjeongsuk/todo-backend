package com.example.todoapp.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예외 응답을 만드는 유일한 지점 (CLAUDE.md 절대 규칙 6). 컨트롤러에서 개별 try-catch로 응답을
 * 조립하지 않는다. Security 필터 단계의 401/403은 여기서 잡히지 않으므로 {@link
 * com.example.todoapp.security.JwtAuthenticationEntryPoint}와 {@link
 * com.example.todoapp.security.JwtAccessDeniedHandler}가 동일한 {@link ApiResponse} 포맷으로 별도 응답한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    /**
     * 필수 쿠키가 없을 때. {@code @CookieValue}가 던진다.
     *
     * <p>이 핸들러가 없으면 아래 generic 핸들러로 떨어져 500이 나간다. 쿠키가 없다는 것은 서버 오류가
     * 아니라 인증 정보가 없다는 뜻이므로 401이 맞다. 실제로 프론트의 라우트 보호 동선에서 미인증
     * 사용자가 접근할 때마다 refresh가 1회 시도되어 이 경로를 탄다.
     */
    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCookie() {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 요청 본문을 읽지 못했을 때. 깨진 JSON, 알 수 없는 enum 값({@code priority: "NOT_A_PRIORITY"}),
     * 형식이 어긋난 날짜({@code dueDate: "not-a-date"}) 등이 모두 여기로 온다.
     *
     * <p>이 핸들러가 없으면 아래 generic 핸들러로 떨어져 500이 나간다. 잘못된 요청은 클라이언트
     * 오류이므로 400이 맞다. 500으로 응답하면 서버 로그에 ERROR로 쌓여 실제 장애와 구분되지 않고,
     * 클라이언트도 재시도하면 될 일인지 판단할 수 없다.
     *
     * <p>예외 메시지에는 Jackson이 내부 클래스명·필드 경로를 담으므로 응답에 싣지 않는다 (PRD NF-06).
     * 어떤 필드가 문제인지는 {@code MethodArgumentNotValidException} 쪽과 달리 알려주지 않는다 —
     * 본문 파싱 자체가 실패해 바인딩된 객체가 없기 때문이다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody() {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        // 스택트레이스·SQL·내부 경로는 응답에 노출하지 않는다 (PRD NF-06). 서버 로그에만 남긴다.
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}
