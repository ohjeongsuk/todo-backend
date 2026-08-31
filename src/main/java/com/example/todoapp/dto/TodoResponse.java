package com.example.todoapp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.todoapp.domain.Priority;
import com.example.todoapp.domain.Todo;

/** 본인 데이터만 조회하므로 사용자 정보를 넣지 않는다(넣으면 불필요한 N+1 조회가 발생한다). */
public record TodoResponse(
        Long id,
        String title,
        String content,
        Priority priority,
        LocalDate dueDate,
        boolean completed,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getContent(),
                todo.getPriority(),
                todo.getDueDate(),
                todo.isCompleted(),
                todo.getCompletedAt(),
                todo.getCreatedAt(),
                todo.getUpdatedAt());
    }
}
