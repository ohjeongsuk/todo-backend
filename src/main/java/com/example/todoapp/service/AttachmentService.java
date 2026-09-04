package com.example.todoapp.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.Attachment;
import com.example.todoapp.domain.AttachmentRepository;
import com.example.todoapp.domain.Todo;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.dto.AttachmentPresignResponse;
import com.example.todoapp.dto.AttachmentResponse;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;
import com.example.todoapp.service.storage.LocalStorageService;
import com.example.todoapp.service.storage.StoragePurpose;
import com.example.todoapp.service.storage.StorageService;
import com.example.todoapp.service.storage.StorageSignature;

/**
 * 첨부 파일의 검증·상태 전이·소유권을 담당한다 (PRD F-46 ~ F-51).
 *
 * <p>{@link StorageService} 인터페이스만 의존하고 구현체가 로컬인지 S3인지 알지 못한다. 이것이 "설정만 바꿔 스토리지를
 * 교체한다"는 요구사항이 성립하는 근거다.
 *
 * <p>소유권 검증은 리포지토리 쿼리 조건으로 강제하고, 실패는 종류를 가리지 않고 <b>404</b>로 응답한다 (CLAUDE.md 절대
 * 규칙 4, PRD NF-02·NF-03·F-48).
 */
@Service
public class AttachmentService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final StorageSignature storageSignature;
    private final long maxFileSize;
    private final Set<String> allowedContentTypes;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            UserRepository userRepository,
            StorageService storageService,
            StorageSignature storageSignature,
            @Value("${app.upload.max-file-size}") long maxFileSize,
            @Value("${app.upload.allowed-content-types}") String allowedContentTypes) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.storageSignature = storageSignature;
        this.maxFileSize = maxFileSize;
        this.allowedContentTypes =
                Set.of(allowedContentTypes.toLowerCase(Locale.ROOT).replace(" ", "").split(","));
    }

    /** 레코드를 PENDING으로 만들고 업로드 URL을 발급한다. 이 시점에는 스토리지에 파일이 없다. */
    @Transactional
    public AttachmentPresignResponse presign(
            Long userId, String filename, String contentType, long declaredSize) {
        String normalizedType = normalizeContentType(contentType);
        if (declaredSize > maxFileSize) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }

        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        String storageKey = buildStorageKey(userId, filename);
        Attachment attachment =
                attachmentRepository.save(
                        Attachment.pending(
                                user,
                                storageService.getType(),
                                storageKey,
                                filename,
                                normalizedType,
                                declaredSize));

        return new AttachmentPresignResponse(
                attachment.getId(),
                storageService.createUploadUrl(attachment.getId(), storageKey, normalizedType));
    }

    /**
     * 업로드 완료를 확정한다. 스토리지에서 실측 크기를 읽어 덮어쓰고 상태를 UPLOADED로 올린다.
     *
     * <p>신고값이 아니라 실측값으로 상한을 다시 검사한다 — presign 때 작은 값을 신고하고 큰 파일을 올리는 우회를 막는다.
     */
    @Transactional
    public AttachmentResponse complete(Long userId, Long attachmentId) {
        Attachment attachment = getOwned(userId, attachmentId);

        long actualSize = storageService.verifyUploaded(attachment.getStorageKey());
        if (actualSize > maxFileSize) {
            storageService.delete(attachment.getStorageKey());
            attachment.softDelete();
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        attachment.markUploaded(actualSize);

        return new AttachmentResponse(
                attachment.getId(),
                storageService.createViewUrl(attachment.getId(), attachment.getStorageKey()));
    }

    /**
     * 본문에 있는 첨부들의 조회 URL을 한 번에 발급한다.
     *
     * <p>존재하지 않거나 타인 소유인 id는 결과에서 조용히 빠진다. 개별로 404를 던지면 "어떤 id가 존재하는지"를 알려주는
     * 셈이 되고, 본문에 죽은 참조가 하나 섞였다고 화면 전체가 실패하는 것도 바람직하지 않다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> viewUrls(Long userId, Collection<Long> attachmentIds) {
        List<Attachment> found =
                attachmentRepository.findAllByIdInAndUser_IdAndDeletedAtIsNull(
                        attachmentIds, userId);

        Map<Long, String> urls = new LinkedHashMap<>();
        for (Attachment attachment : found) {
            urls.put(
                    attachment.getId(),
                    storageService.createViewUrl(attachment.getId(), attachment.getStorageKey()));
        }
        return urls;
    }

    /** Soft Delete만 한다. 실제 파일은 정리 배치가 유예 기간 후에 지운다 (CLAUDE.md 절대 규칙 5). */
    @Transactional
    public void delete(Long userId, Long attachmentId) {
        getOwned(userId, attachmentId).softDelete();
    }

    /**
     * 할 일 본문에 남아 있는 첨부를 연결하고, 사라진 첨부는 Soft Delete한다 (PRD F-49, F-51).
     *
     * <p><b>반드시 sanitize를 거친 HTML을 넘겨야 한다.</b> 정화 전 HTML을 넘기면 Jsoup이 제거할 태그 안의 첨부까지
     * {@code LINKED}로 승격되어, 본문에 존재하지 않는 파일이 정리 배치의 대상에서도 빠진 채 영구 보존된다.
     *
     * @param sanitizedHtml {@link HtmlSanitizer#sanitize}를 통과한 본문
     */
    @Transactional
    public void syncTodoAttachments(Long userId, Todo todo, String sanitizedHtml) {
        Set<Long> referencedIds = extractAttachmentIds(sanitizedHtml);

        List<Attachment> owned =
                referencedIds.isEmpty()
                        ? List.of()
                        : attachmentRepository.findAllByIdInAndUser_IdAndDeletedAtIsNull(
                                referencedIds, userId);

        // 개수가 다르면 존재하지 않거나 타인 소유인 id가 섞인 것이다. 어느 쪽이든 404다
        // (CLAUDE.md 절대 규칙 4 - 존재 여부를 노출하지 않는다).
        if (owned.size() != referencedIds.size()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        for (Attachment attachment : owned) {
            attachment.linkTo(todo);
        }

        // 기존에 연결돼 있었으나 본문에서 사라진 첨부를 정리한다. 수정(update) 경로에서만 실제로 걸린다.
        if (todo.getId() != null) {
            for (Attachment previous :
                    attachmentRepository.findAllByTodo_IdAndDeletedAtIsNull(todo.getId())) {
                if (!referencedIds.contains(previous.getId())) {
                    previous.softDelete();
                }
            }
        }
    }

    /**
     * 본문에서 {@code data-attachment-id} 값을 모은다.
     *
     * <p>숫자가 아닌 값은 조용히 건너뛴다 — sanitize를 통과한 HTML이라도 속성 값 자체는 사용자가 넣은 문자열이므로
     * 파싱 실패로 500이 나면 안 된다.
     */
    private Set<Long> extractAttachmentIds(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Element img : Jsoup.parse(sanitizedHtml).select("img[data-attachment-id]")) {
            String raw = img.attr("data-attachment-id");
            try {
                ids.add(Long.valueOf(raw));
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 참조는 무시한다. 어차피 조회 URL을 받지 못해 화면에도 뜨지 않는다.
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // 로컬 스토리지 전용 경로 (서명 토큰으로 인가한다 - JWT 필터를 거치지 않는다)
    // ------------------------------------------------------------------

    /**
     * 로컬 업로드 수신. 서명 토큰으로 인가하므로 로그인 사용자 정보 없이 동작한다.
     *
     * <p>크기 제한은 컨트롤러의 {@code Content-Length} 선검사와 이 메서드의 스트림 바이트 카운트로 이중 방어한다
     * (PRD NF-34).
     */
    @Transactional
    public void receiveLocalUpload(Long attachmentId, String token, InputStream body) {
        storageSignature.verify(token, attachmentId, StoragePurpose.UPLOAD);

        Attachment attachment =
                attachmentRepository
                        .findById(attachmentId)
                        .filter(a -> !a.isDeleted())
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!attachment.isPending()) {
            // 이미 업로드가 끝났거나 연결된 첨부에 덮어쓰기를 허용하지 않는다.
            throw new ApiException(ErrorCode.UPLOAD_NOT_COMPLETED);
        }

        LocalStorageService local = requireLocalStorage();
        if (local.exists(attachment.getStorageKey())) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_COMPLETED);
        }
        local.writeStream(attachment.getStorageKey(), body, maxFileSize);
    }

    /** 로컬 조회. 브라우저 {@code <img>}가 직접 호출하므로 서명 토큰으로만 인가한다. */
    @Transactional(readOnly = true)
    public LocalFile openLocalFile(Long attachmentId, String token) {
        storageSignature.verify(token, attachmentId, StoragePurpose.VIEW);

        Attachment attachment =
                attachmentRepository
                        .findById(attachmentId)
                        .filter(a -> !a.isDeleted())
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        LocalStorageService local = requireLocalStorage();
        return new LocalFile(
                local.openStream(attachment.getStorageKey()),
                attachment.getContentType(),
                attachment.getFileSize());
    }

    /** 로컬 파일 스트림과 응답 헤더에 필요한 메타데이터. */
    public record LocalFile(InputStream stream, String contentType, long size) {}

    // ------------------------------------------------------------------
    // 내부 헬퍼
    // ------------------------------------------------------------------

    /** 소유권 검증. 실패는 존재 여부를 노출하지 않도록 전부 404다 (CLAUDE.md 절대 규칙 4). */
    private Attachment getOwned(Long userId, Long attachmentId) {
        return attachmentRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(attachmentId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private LocalStorageService requireLocalStorage() {
        if (storageService instanceof LocalStorageService local) {
            return local;
        }
        // S3 프로파일에서는 이 엔드포인트가 쓰이지 않는다. 존재를 노출하지 않도록 404로 응답한다.
        throw new ApiException(ErrorCode.NOT_FOUND);
    }

    /**
     * 확장자만 믿지 않고 {@code contentType}을 화이트리스트로 검증한다 (PRD F-47).
     *
     * <p>{@code image/svg+xml}은 목록에 없다. SVG는 스크립트를 실행할 수 있어 XSS 벡터다.
     */
    private String normalizeContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        // "image/png; charset=binary" 처럼 파라미터가 붙어 오는 경우를 잘라낸다.
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if (!allowedContentTypes.contains(normalized)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return normalized;
    }

    /**
     * 저장 키는 <b>서버만 생성한다</b> (PRD NF-33). 원본 파일명은 확장자만 쓰고, 본체는 UUID로 대체해 경로 조작·중복·
     * 인코딩 문제를 한 번에 없앤다.
     */
    private String buildStorageKey(Long userId, String filename) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return "todos/%d/%s/%s/%s%s"
                .formatted(
                        userId,
                        now.format(YEAR),
                        now.format(MONTH),
                        UUID.randomUUID(),
                        safeExtension(filename));
    }

    /** 확장자만 뽑아낸다. 없거나 이상하면 빈 문자열이다 (파일명 자체는 저장 키에 쓰지 않는다). */
    private String safeExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{1,10}")) {
            return "";
        }
        return "." + ext;
    }
}
