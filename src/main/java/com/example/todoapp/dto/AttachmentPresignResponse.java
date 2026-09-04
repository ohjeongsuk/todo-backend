package com.example.todoapp.dto;

/**
 * 업로드 URL 발급 응답.
 *
 * <p><b>{@code storageKey}를 담지 않는다</b> (PRD NF-33). 클라이언트가 스토리지 경로를 되돌려보낼 통로 자체를
 * 없애 경로 조작 공격면을 제거한다. 이후 모든 요청은 {@code attachmentId}만으로 이뤄진다.
 *
 * <p>{@code uploadUrl}이 로컬 엔드포인트인지 S3 presigned URL인지 클라이언트는 알 필요가 없다 (PRD F-50).
 */
public record AttachmentPresignResponse(Long attachmentId, String uploadUrl) {}
