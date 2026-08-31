package com.example.todoapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.todoapp.dto.validation.ValidPassword;

public record SignupRequest(
        @NotBlank @Email String email,
        @ValidPassword String password,
        @NotBlank @Size(min = 1, max = 50) String nickname) {}
