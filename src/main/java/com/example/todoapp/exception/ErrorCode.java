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
    // 첨부 파일 업로드 (PRD F-47, NF-34). INVALID_INPUT으로 묶지 않는 이유는 프론트가
    // 사용자에게 "용량 초과"와 "지원하지 않는 형식"을 다르게 안내해야 하기 때문이다.
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 허용 범위를 넘었습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    UPLOAD_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "업로드가 완료되지 않았습니다."),
    // 비밀번호 재설정 요청 rate limit (PRD NF-30). 응답이 요청 빈도에만 의존하고 계정 존재 여부와는
    // 무관하므로 NF-31(계정 존재 미노출)에 위배되지 않는다.
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
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
