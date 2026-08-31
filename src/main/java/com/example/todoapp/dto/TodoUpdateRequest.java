package com.example.todoapp.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.todoapp.domain.Priority;

/** completed 필드가 없다 — PUT 저장은 전체 교체이지만 완료 상태는 덮어쓰지 않는다 (ROADMAP Phase 4 TODO-10). */
public record TodoUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Priority priority,
        LocalDate dueDate) {}
