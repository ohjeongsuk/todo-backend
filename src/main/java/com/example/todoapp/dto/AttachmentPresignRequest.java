package com.example.todoapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 업로드 URL 발급 요청 (PRD F-46).
 *
 * <p>{@code fileSize}는 클라이언트 신고값이라 <b>신뢰하지 않는다.</b> 불필요한 업로드를 미리 막는 용도일 뿐이며, 실제
 * 크기는 업로드 중 바이트를 세고 complete 시점에 스토리지에서 다시 확인한다 (PRD NF-34).
 */
public record AttachmentPresignRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long fileSize) {}
