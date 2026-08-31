package com.example.todoapp.dto;

/** Refresh Token은 응답 본문에 담지 않는다. httpOnly 쿠키로만 전달한다 (PRD NF-26). */
public record TokenResponse(String accessToken) {}
