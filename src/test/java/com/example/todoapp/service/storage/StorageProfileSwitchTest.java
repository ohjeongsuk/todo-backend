package com.example.todoapp.service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code app.storage.type} 설정값만으로 스토리지 구현체가 교체되는지 검증한다 (PRD F-50).
 *
 * <p>실제 AWS 자격증명이나 버킷은 필요 없다. {@code S3Client}·{@code S3Presigner} 빈 생성은 네트워크 호출을
 * 하지 않는다(지연 연결) — 어떤 빈이 등록됐는지만 확인하면 조건부 배선을 검증할 수 있다. 실제 S3 동작(업로드·조회·
 * presigned URL 서명 일치 등)은 이 테스트의 범위가 아니며 실제 버킷으로 수동 검증해야 한다.
 *
 * <p>{@code properties}는 {@code @ActiveProfiles("test")}가 불러오는 {@code application-test.properties}의
 * {@code app.storage.type=local}보다 우선순위가 높아, 이 클래스 안에서만 s3로 전환된다.
 */
@SpringBootTest(
        properties = {
            "app.storage.type=s3",
            "app.storage.s3.bucket=context-test-bucket",
            "app.storage.s3.region=ap-northeast-2",
            "app.storage.url-expiry-minutes=30",
            "app.storage.signing-secret=context-test-storage-signing-secret-32bytes-min"
        })
@ActiveProfiles("test")
class StorageProfileSwitchTest {

    @Autowired private ApplicationContext context;

    @Test
    void app_storage_type가_s3면_S3StorageService만_등록된다() {
        StorageService storageService = context.getBean(StorageService.class);

        assertThat(storageService).isInstanceOf(S3StorageService.class);
        assertThat(storageService.getType()).isEqualTo(com.example.todoapp.domain.StorageType.S3);

        // AttachmentService도 이 인터페이스만 주입받는다 - 구현체 전환에 다른 코드 변경이 없다는 증거다.
        assertThatThrownBy(() -> context.getBean(LocalStorageService.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
