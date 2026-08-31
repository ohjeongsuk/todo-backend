package com.example.todoapp.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** 페이지네이션 목록 응답 공통 포맷. {@code ApiResponse.data} 안에 담겨 반환된다. */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
