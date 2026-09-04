package com.example.todoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 본문에 첨부한 이미지의 메타데이터 (PRD F-46 ~ F-51).
 *
 * <p>파일 실체는 스토리지(로컬 디렉터리 또는 S3)에 있고 이 엔티티는 참조와 상태만 관리한다.
 *
 * <p>{@code todo}가 null을 허용하는 이유는 에디터에서 <b>할 일을 저장하기 전에</b> 이미지가 먼저 업로드되기 때문이다. 업로드
 * 시점에는 연결할 할 일이 아직 없다. 할 일 저장 시 본문에 실제로 남아 있는 첨부만 {@link #linkTo}로 연결한다.
 *
 * <p>{@code storageKey}는 <b>서버만 생성</b>하며 API 응답에 노출하지 않는다 (PRD NF-33). 클라이언트가 경로를
 * 되돌려보낼 통로 자체를 없애 경로 조작 공격면을 제거한다.
 */
@Entity
@Table(
        name = "attachments",
        indexes = {
            @Index(name = "idx_attachments_todo_deleted", columnList = "todo_id, deleted_at"),
            @Index(name = "idx_attachments_user_deleted", columnList = "user_id, deleted_at"),
            @Index(name = "idx_attachments_status_created", columnList = "status, created_at")
        })
public class Attachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 할 일. 업로드 시점에는 null이고 할 일 저장 시 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id")
    private Todo todo;

    /** 업로더. 소유권 검증의 기준이며 할 일 연결 여부와 무관하게 항상 존재한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** bytes. presign 시 클라이언트 신고값으로 채우고 complete 시 실측값으로 덮어쓴다. */
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachmentStatus status;

    protected Attachment() {}

    private Attachment(
            User user,
            StorageType storageType,
            String storageKey,
            String originalFilename,
            String contentType,
            long declaredSize) {
        this.user = user;
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = declaredSize;
        this.status = AttachmentStatus.PENDING;
    }

    /** presign 단계에서 레코드만 먼저 만든다. 이 시점에는 스토리지에 파일이 없다. */
    public static Attachment pending(
            User user,
            StorageType storageType,
            String storageKey,
            String originalFilename,
            String contentType,
            long declaredSize) {
        return new Attachment(
                user, storageType, storageKey, originalFilename, contentType, declaredSize);
    }

    /**
     * 업로드 완료를 확정한다. 크기는 클라이언트 신고값이 아니라 스토리지에서 읽은 <b>실측값</b>으로 덮어쓴다 — 신고값은 위조할 수
     * 있기 때문이다.
     */
    public void markUploaded(long actualSize) {
        this.fileSize = actualSize;
        this.status = AttachmentStatus.UPLOADED;
    }

    /** 할 일 본문에 실제로 존재하는 첨부로 확정한다. */
    public void linkTo(Todo todo) {
        this.todo = todo;
        this.status = AttachmentStatus.LINKED;
    }

    public boolean isPending() {
        return status == AttachmentStatus.PENDING;
    }

    public boolean isUploaded() {
        return status == AttachmentStatus.UPLOADED;
    }

    public Long getId() {
        return id;
    }

    public Todo getTodo() {
        return todo;
    }

    public User getUser() {
        return user;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public AttachmentStatus getStatus() {
        return status;
    }
}
