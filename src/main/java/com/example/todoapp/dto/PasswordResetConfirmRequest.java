package com.example.todoapp.dto;

import jakarta.validation.constraints.NotBlank;

import com.example.todoapp.dto.validation.ValidPassword;

public record PasswordResetConfirmRequest(
        @NotBlank String token, @ValidPassword String newPassword) {}
