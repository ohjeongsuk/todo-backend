package com.example.todoapp.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 본문에 있는 첨부들의 조회 URL을 <b>한 번에</b> 요청한다.
 *
 * <p>단건 조회 엔드포인트를 두면 본문 이미지 수만큼 요청이 나간다. 이미지 5장이면 왕복이 5번이다.
 */
public record AttachmentViewUrlsRequest(@NotEmpty @Size(max = 100) List<Long> attachmentIds) {}
