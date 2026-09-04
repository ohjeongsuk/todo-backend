package com.example.todoapp.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.Attachment;
import com.example.todoapp.domain.AttachmentRepository;
import com.example.todoapp.domain.AttachmentStatus;
import com.example.todoapp.service.storage.StorageService;

/**
 * 고아 첨부 파일 정리 (PRD F-51). 하루 1회 새벽에 돈다.
 *
 * <p>상태마다 정책이 다르다. 이것이 {@link AttachmentStatus}를 두 개가 아니라 세 개로 나눈 이유다.
 *
 * <ul>
 *   <li>{@code PENDING} — presign만 하고 파일을 올리지 않았다. 파일이 없으므로 레코드만 정리한다. 1시간이면 충분하다
 *   <li>{@code UPLOADED} — 올렸지만 사용자가 할 일을 저장하지 않았다. 실제 파일까지 지운다. 작성 중일 수 있어 24시간을 준다
 *   <li>Soft Delete됨 — 본문에서 지워졌거나 사용자가 삭제했다. 7일 유예 후 파일을 지운다
 * </ul>
 *
 * <p>Soft Delete 시점에 파일을 즉시 지우지 않는 이유는 되돌릴 수 없기 때문이다. DB 행은 {@code deleted_at}으로
 * 남지만 파일을 지워버리면 복구할 방법이 없다 (CLAUDE.md 절대 규칙 5의 취지).
 *
 * <p>한 트랜잭션이 길어지지 않도록 배치 크기를 제한한다. 대상이 많으면 다음 실행에서 이어 처리된다.
 */
@Service
public class AttachmentCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupScheduler.class);

    /** 한 번에 처리할 최대 건수. 트랜잭션이 길어져 커넥션을 오래 잡는 것을 막는다. */
    private static final int BATCH_SIZE = 200;

    private static final long PENDING_GRACE_HOURS = 1;
    private static final long UPLOADED_GRACE_HOURS = 24;
    private static final long DELETED_GRACE_DAYS = 7;

    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;

    public AttachmentCleanupScheduler(
            AttachmentRepository attachmentRepository, StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
    }

    /** 매일 04:00(서버 시각)에 실행한다. 트래픽이 적은 시간대를 고른다. */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanUp() {
        int pending = cleanUpPending();
        int uploaded = cleanUpUploaded();
        int purged = purgeDeletedFiles();
        if (pending + uploaded + purged > 0) {
            log.info("첨부 정리 완료 - PENDING {}건, UPLOADED {}건, 파일 삭제 {}건", pending, uploaded, purged);
        }
    }

    /** 파일이 없는 레코드다. Soft Delete로 목록에서 걷어내기만 한다. */
    @Transactional
    int cleanUpPending() {
        LocalDateTime cutoff = now().minusHours(PENDING_GRACE_HOURS);
        List<Attachment> targets =
                attachmentRepository.findAllByStatusAndDeletedAtIsNullAndCreatedAtBefore(
                        AttachmentStatus.PENDING, cutoff, batch());
        targets.forEach(Attachment::softDelete);
        return targets.size();
    }

    /** 업로드는 끝났지만 할 일에 연결되지 않은 첨부. 실제 파일까지 지운다. */
    @Transactional
    int cleanUpUploaded() {
        LocalDateTime cutoff = now().minusHours(UPLOADED_GRACE_HOURS);
        List<Attachment> targets =
                attachmentRepository.findAllByStatusAndDeletedAtIsNullAndCreatedAtBefore(
                        AttachmentStatus.UPLOADED, cutoff, batch());
        for (Attachment attachment : targets) {
            storageService.delete(attachment.getStorageKey());
            attachment.softDelete();
        }
        return targets.size();
    }

    /**
     * Soft Delete된 지 유예 기간이 지난 첨부의 실제 파일을 지운다.
     *
     * <p>DB 행은 그대로 둔다. 물리 삭제를 하지 않는다는 규칙은 DB에 대한 것이며, 스토리지 용량 회수는 별개 문제다.
     */
    @Transactional(readOnly = true)
    int purgeDeletedFiles() {
        LocalDateTime cutoff = now().minusDays(DELETED_GRACE_DAYS);
        List<Attachment> targets =
                attachmentRepository.findAllByDeletedAtIsNotNullAndDeletedAtBefore(cutoff, batch());
        targets.forEach(attachment -> storageService.delete(attachment.getStorageKey()));
        return targets.size();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static Pageable batch() {
        return PageRequest.of(0, BATCH_SIZE);
    }
}
