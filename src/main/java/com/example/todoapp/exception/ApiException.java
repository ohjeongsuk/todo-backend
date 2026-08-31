package com.example.todoapp.exception;

/** 서비스 계층에서 의도적으로 던지는 예외. {@link ErrorCode}가 응답 형식·HTTP 상태를 결정한다. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
