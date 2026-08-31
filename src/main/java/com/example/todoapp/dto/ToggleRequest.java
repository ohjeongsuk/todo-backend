package com.example.todoapp.dto;

/** 목표 상태를 그대로 서버에 전송한다 — 서버가 값을 뒤집지 않는다. */
public record ToggleRequest(boolean completed) {}
