package com.example.todoapp.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬 스토리지의 경로 조작 방어(PRD NF-33)와 크기 제한 이중 방어(PRD NF-34) 검증.
 *
 * <p>Spring 컨텍스트 없이 돈다. {@code @TempDir}가 테스트마다 격리된 디렉터리를 주므로 저장소에 잔여 파일이 남지 않는다.
 */
class LocalStorageServiceTest {

    private static final String SECRET = "unit-test-storage-signing-secret-32bytes-min";

    @TempDir Path tempDir;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage =
                new LocalStorageService(
                        tempDir.toString(),
                        "http://localhost:8080",
                        new StorageSignature(SECRET, 30));
        storage.prepareBaseDirectory();
    }

    @Test
    @DisplayName("상위 디렉터리 탈출 키는 404로 거부된다")
    void rejectsPathTraversal() {
        // storageKey는 서버만 생성하므로 실제로는 도달할 수 없는 경로다. 심층 방어를 고정해 둔다.
        assertThatThrownBy(() -> storage.resolveSafe("../../etc/passwd"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        assertThatThrownBy(() -> storage.resolveSafe("todos/1/../../../outside.png"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("정상 키는 base 디렉터리 하위로 해석된다")
    void resolvesNormalKey() {
        Path resolved = storage.resolveSafe("todos/1/2026/09/abc.png");

        // AssertJ의 Path.startsWith 는 toRealPath()를 써서 파일이 실제로 존재해야 한다.
        // 여기서는 아직 만들지 않은 경로를 검증하므로 java.nio 의 순수 경로 비교를 쓴다.
        assertThat(resolved.startsWith(tempDir)).isTrue();
        assertThat(resolved.toString()).endsWith("abc.png");
    }

    @Test
    @DisplayName("업로드한 바이트 수를 반환하고 실측 크기와 일치한다")
    void writesStreamAndReportsSize() {
        byte[] data = "hello attachment".getBytes(StandardCharsets.UTF_8);

        long written =
                storage.writeStream("todos/1/2026/09/a.png", new ByteArrayInputStream(data), 1024);

        assertThat(written).isEqualTo(data.length);
        assertThat(storage.verifyUploaded("todos/1/2026/09/a.png")).isEqualTo(data.length);
        assertThat(storage.exists("todos/1/2026/09/a.png")).isTrue();
    }

    @Test
    @DisplayName("상한을 넘으면 중단하고 부분 파일을 남기지 않는다")
    void abortsAndCleansUpWhenTooLarge() throws IOException {
        byte[] data = new byte[4096];
        String key = "todos/1/2026/09/big.png";

        assertThatThrownBy(() -> storage.writeStream(key, new ByteArrayInputStream(data), 1024))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);

        // 부분 파일이 남으면 재업로드가 409로 막히고 디스크도 새어 나간다.
        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    @DisplayName("업로드되지 않은 키는 UPLOAD_NOT_COMPLETED로 응답한다")
    void rejectsVerifyOnMissingFile() {
        assertThatThrownBy(() -> storage.verifyUploaded("todos/1/2026/09/missing.png"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_NOT_COMPLETED);
    }

    @Test
    @DisplayName("발급한 URL은 서명 토큰을 달고 있고 용도가 구분된다")
    void createsSignedUrls() {
        String uploadUrl = storage.createUploadUrl(7L, "todos/1/a.png", "image/png");
        String viewUrl = storage.createViewUrl(7L, "todos/1/a.png");

        assertThat(uploadUrl).startsWith("http://localhost:8080/api/attachments/7/upload?token=");
        assertThat(viewUrl).startsWith("http://localhost:8080/api/attachments/7/raw?token=");
        assertThat(uploadUrl).isNotEqualTo(viewUrl);
    }
}
