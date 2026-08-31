package com.example.todoapp.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 소유권 검증은 쿼리 단계에서 강제한다 (PRD NF-02). 모든 조회 메서드는 {@code userId}를 조건에 포함하고,
 * 삭제된 항목은 {@code deletedAt IS NULL} 조건으로 제외한다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

    Page<Todo> findAllByUser_IdAndDeletedAtIsNull(Long userId, Pageable pageable);

    /**
     * {@code completed}·{@code keyword}는 미지정(null) 시 조건에서 제외된다. 제목 검색은 대소문자를
     * 무시하며, {@code idx_todos_title_lower}(수동 생성, {@code db/add-title-index.sql})가 {@code
     * lower(title)} 서술어와 매칭되어 인덱스를 탄다.
     */
    @Query(
            "select t from Todo t "
                    + "where t.user.id = :userId and t.deletedAt is null "
                    + "and (:completed is null or t.completed = :completed) "
                    + "and (cast(:keyword as string) is null or lower(t.title) like"
                    + " lower(concat('%', cast(:keyword as string), '%')))")
    Page<Todo> search(
            @Param("userId") Long userId,
            @Param("completed") Boolean completed,
            @Param("keyword") String keyword,
            Pageable pageable);
}
