package com.example.todoapp.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.todoapp.domain.Priority;

public record TodoCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Priority priority,
        LocalDate dueDate) {}
