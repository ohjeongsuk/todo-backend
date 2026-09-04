package com.example.todoapp.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 소유권 검증은 쿼리 단계에서 강제한다 (PRD NF-02, F-48). 조회 메서드는 {@code userId}를 조건에 포함하고 삭제된 항목은
 * {@code deletedAt IS NULL} 조건으로 제외한다.
 *
 * <p>정리 배치용 메서드는 예외적으로 {@code userId}를 받지 않는다 — 전체 사용자를 대상으로 도는 시스템 작업이며 HTTP 요청
 * 경로에서 호출되지 않기 때문이다.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

    /**
     * 본문에서 수집한 첨부 ID를 한 번에 조회한다. 루프 안에서 단건 조회를 반복하면 본문 이미지 수만큼 쿼리가 나가므로 반드시 이
     * 메서드를 쓴다.
     *
     * <p>반환 개수가 요청한 ID 개수와 다르면 존재하지 않거나 타인 소유인 ID가 섞인 것이다.
     */
    List<Attachment> findAllByIdInAndUser_IdAndDeletedAtIsNull(Collection<Long> ids, Long userId);

    /** 할 일에 현재 연결된 첨부. 본문에서 사라진 첨부를 가려내는 데 쓴다. */
    List<Attachment> findAllByTodo_IdAndDeletedAtIsNull(Long todoId);

    /** 고아 파일 정리 배치용. 상태별로 서로 다른 유예 기간을 적용한다. */
    List<Attachment> findAllByStatusAndDeletedAtIsNullAndCreatedAtBefore(
            AttachmentStatus status, LocalDateTime cutoff, Pageable pageable);

    /** Soft Delete된 지 유예 기간이 지나 실제 파일까지 지울 대상. */
    List<Attachment> findAllByDeletedAtIsNotNullAndDeletedAtBefore(
            LocalDateTime cutoff, Pageable pageable);
}
