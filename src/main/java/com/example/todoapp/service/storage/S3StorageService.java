package com.example.todoapp.service.storage;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import com.example.todoapp.domain.StorageType;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

/**
 * S3 스토리지 구현체. {@code app.storage.type=s3}일 때만 등록된다.
 *
 * <p><b>presigned PUT은 {@code contentType}이 서명에 포함된다.</b> 여기서 서명에 넣은 값과 프론트가 실제 PUT
 * 요청 헤더에 싣는 값이 한 글자라도 다르면 S3가 {@code SignatureDoesNotMatch}(403)로 거부한다. 로컬 스토리지는
 * Content-Type을 검증하지 않으므로 이 함정은 로컬 테스트에서는 드러나지 않고 이 구현으로 전환한 뒤에만 나타난다.
 *
 * <p>{@code attachmentId}는 인터페이스 시그니처를 {@link LocalStorageService}와 맞추기 위해 받지만, S3
 * presigned URL 생성에는 필요 없다.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration expiry;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.url-expiry-minutes}") long expiryMinutes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.expiry = Duration.ofMinutes(expiryMinutes);
    }

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }

    @Override
    public String createUploadUrl(Long attachmentId, String storageKey, String contentType) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .contentType(contentType)
                        .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    @Override
    public String createViewUrl(Long attachmentId, String storageKey) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder().bucket(bucket).key(storageKey).build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    @Override
    public long verifyUploaded(String storageKey) {
        try {
            return s3Client.headObject(r -> r.bucket(bucket).key(storageKey)).contentLength();
        } catch (NoSuchKeyException e) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_COMPLETED);
        }
    }

    @Override
    public void delete(String storageKey) {
        // S3 DeleteObject는 키가 없어도 예외를 던지지 않는다. LocalStorageService와 동일하게
        // "존재하지 않아도 성공"이 자연스럽게 성립해 별도 처리가 필요 없다.
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    }
}
