package com.example.todoapp.exception;

import org.springframework.http.HttpStatus;

/** 응답 {@code error.code}에 실리는 값과 그에 대응하는 HTTP 상태 코드. */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    // 로그인 실패(미가입 이메일/오답), 토큰 없음/만료/조작, refresh 실패를 모두 UNAUTHORIZED로
    // 통일한다. 프론트는 error.code가 아니라 HTTP 401 상태 자체로 refresh 시도 여부를 판단하고,
    // 로그인 실패 메시지가 계정 존재 여부를 드러내지 않아야 하기 때문이다 (PRD NF-31).
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "링크가 만료되었거나 이미 사용되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
