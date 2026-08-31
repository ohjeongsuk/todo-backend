package com.example.todoapp.exception;

/** 모든 API 응답이 따르는 공통 포맷. 성공 시 {@code data}, 실패 시 {@code error}만 채워진다. */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public record ErrorResponse(String code, String message, Object details) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, Object details) {
        return new ApiResponse<>(
                false, null, new ErrorResponse(code.name(), code.getDefaultMessage(), details));
    }

    public static <T> ApiResponse<T> error(ErrorCode code) {
        return error(code, null);
    }
}
