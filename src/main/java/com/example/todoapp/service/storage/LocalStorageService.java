package com.example.todoapp.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.todoapp.domain.StorageType;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

/**
 * 로컬 디스크 스토리지 구현체. {@code app.storage.type=local}일 때만 등록된다.
 *
 * <p>업로드·조회 URL은 백엔드 자신의 엔드포인트를 가리키며 <b>쿼리 서명 토큰</b>이 붙는다. S3 presigned URL과 형태가
 * 같아져 프론트엔드가 두 스토리지를 구분할 필요가 없어진다 (PRD F-50).
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path baseDir;
    private final String baseUrl;
    private final StorageSignature signature;

    public LocalStorageService(
            @Value("${app.storage.local.base-dir}") String baseDir,
            @Value("${app.storage.local.base-url}") String baseUrl,
            StorageSignature signature) {
        // toAbsolutePath().normalize()를 여기서 한 번 해두면 이후 경로 비교가 항상 절대경로 기준이 된다.
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signature = signature;
    }

    /** 기동 시 저장 디렉터리를 준비한다. 없으면 만든다 (첫 업로드에서 실패하지 않도록). */
    @PostConstruct
    void prepareBaseDirectory() {
        try {
            Files.createDirectories(baseDir);
            log.info("로컬 첨부 저장 디렉터리: {}", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리를 만들 수 없습니다: " + baseDir, e);
        }
    }

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    @Override
    public String createUploadUrl(Long attachmentId, String storageKey, String contentType) {
        return "%s/api/attachments/%d/upload?token=%s"
                .formatted(
                        baseUrl, attachmentId, encode(sign(attachmentId, StoragePurpose.UPLOAD)));
    }

    @Override
    public String createViewUrl(Long attachmentId, String storageKey) {
        return "%s/api/attachments/%d/raw?token=%s"
                .formatted(baseUrl, attachmentId, encode(sign(attachmentId, StoragePurpose.VIEW)));
    }

    @Override
    public long verifyUploaded(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_COMPLETED);
        }
        try {
            return Files.size(target);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_COMPLETED);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException e) {
            // 정리 배치가 재실행되므로 실패를 전파하지 않는다. 한 건 때문에 배치 전체가 멈추면 안 된다.
            log.warn("첨부 파일 삭제 실패: {}", storageKey, e);
        }
    }

    /** 파일이 이미 있으면 true. 업로드 중복 요청을 409로 거르는 데 쓴다. */
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolveSafe(storageKey));
    }

    /**
     * 요청 본문 스트림을 파일로 기록하며 바이트를 센다. 상한을 넘으면 <b>즉시 중단하고 부분 파일을 지운다</b>.
     *
     * <p>{@code Content-Length}는 위조할 수 있으므로 헤더 검사만으로는 부족하다. 실제로 읽은 바이트를 세는 이 경로가
     * 크기 제한의 실질 방어선이다 (PRD NF-34).
     *
     * @return 기록한 바이트 수
     */
    public long writeStream(String storageKey, InputStream in, long maxBytes) {
        Path target = resolveSafe(storageKey);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        long written = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream out =
                Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > maxBytes) {
                    // try-with-resources가 닫은 뒤 지워야 Windows에서 잠금 충돌이 없다.
                    out.close();
                    deleteQuietly(target);
                    throw new ApiException(ErrorCode.FILE_TOO_LARGE);
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            deleteQuietly(target);
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return written;
    }

    /** 스트림으로 읽기 위해 파일 경로를 연다. */
    public InputStream openStream(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * 경로 조작 방어 (PRD NF-33).
     *
     * <p>{@code storageKey}는 서버만 생성하고 API 응답에도 넣지 않으므로 클라이언트가 제어할 수 없다. 그래도 심층
     * 방어로 남긴다 — 정규화한 최종 경로가 base 디렉터리 하위인지 확인하고, 벗어나면 거부한다.
     */
    Path resolveSafe(String storageKey) {
        Path resolved = baseDir.resolve(storageKey).toAbsolutePath().normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return resolved;
    }

    private String sign(Long attachmentId, StoragePurpose purpose) {
        return signature.sign(attachmentId, purpose);
    }

    private static String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            log.warn("부분 업로드 파일 삭제 실패: {}", target);
        }
    }
}
