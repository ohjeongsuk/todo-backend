package com.example.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 소유권 검증은 쿼리 단계에서 강제한다 (PRD NF-02). 모든 조회 메서드는 {@code userId}를 조건에 포함하고,
 * 삭제된 항목은 {@code deletedAt IS NULL} 조건으로 제외한다.
 *
 * <p>페이지네이션·필터·검색이 포함된 커스텀 쿼리는 Phase 4(Todo API)에서 추가한다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

    Page<Todo> findAllByUser_IdAndDeletedAtIsNull(Long userId, Pageable pageable);
}
