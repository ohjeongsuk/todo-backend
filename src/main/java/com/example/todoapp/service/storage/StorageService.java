package com.example.todoapp.service.storage;

import com.example.todoapp.domain.StorageType;

/**
 * 첨부 파일 스토리지의 추상 경계 (PRD F-50).
 *
 * <p>{@code AttachmentService}는 이 인터페이스만 의존하고 어떤 구현체가 등록됐는지 알지 못한다. 이것이 "설정만 바꿔
 * 스토리지를 교체한다"는 요구사항의 기술적 근거다. 구현체는 {@code app.storage.type} 값에 따라
 * {@code @ConditionalOnProperty}로 <b>하나만</b> 등록된다.
 *
 * <p>URL 발급 메서드가 {@code attachmentId}를 받는 이유는 로컬 구현이 서명 토큰을 만들 때 대상 식별자가 필요하기
 * 때문이다. S3 구현은 쓰지 않지만, 두 구현이 같은 시그니처를 갖도록 인터페이스에 포함한다.
 */
public interface StorageService {

    StorageType getType();

    /**
     * 업로드용 URL을 발급한다. S3는 presigned PUT, 로컬은 서명 토큰이 붙은 백엔드 엔드포인트다.
     *
     * <p>프론트엔드는 이 URL이 어디를 가리키는지 알 필요 없이 그대로 PUT을 보낸다. 로컬 엔드포인트에도 서명 토큰을 붙이는
     * 이유가 여기 있다 — JWT를 요구하면 프론트가 로컬/S3를 분기해야 하는데, S3 presigned PUT은
     * {@code Authorization} 헤더가 붙으면 서명 검증에 실패한다.
     */
    String createUploadUrl(Long attachmentId, String storageKey, String contentType);

    /** 조회용 URL을 발급한다. S3는 presigned GET, 로컬은 서명 토큰이 붙은 백엔드 엔드포인트다. */
    String createViewUrl(Long attachmentId, String storageKey);

    /**
     * 업로드 완료 후 실제 파일의 존재와 크기를 확인해 <b>실측 바이트 수</b>를 반환한다.
     *
     * <p>클라이언트가 presign 시 신고한 크기는 위조할 수 있으므로 이 값으로 덮어쓴다.
     *
     * @throws com.example.todoapp.exception.ApiException 파일이 없으면 {@code UPLOAD_NOT_COMPLETED}
     */
    long verifyUploaded(String storageKey);

    /** 실제 파일을 삭제한다. 존재하지 않아도 예외를 던지지 않는다 (정리 배치가 재실행될 수 있다). */
    void delete(String storageKey);
}
