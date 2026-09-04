package com.example.todoapp.dto;

/** 업로드 확정 응답. 곧바로 본문 이미지에 주입할 조회 URL을 함께 준다. */
public record AttachmentResponse(Long attachmentId, String viewUrl) {}
