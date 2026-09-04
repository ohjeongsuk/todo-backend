package com.example.todoapp.service;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.Todo;
import com.example.todoapp.domain.TodoRepository;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.dto.TodoCreateRequest;
import com.example.todoapp.dto.TodoUpdateRequest;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

/**
 * Todo의 목록 조회·생성·단건 조회·수정·토글·삭제를 담당한다. 소유권 검증은 {@link
 * TodoRepository#findByIdAndUser_IdAndDeletedAtIsNull}이 쿼리 단계에서 강제하므로(PRD NF-02), 이
 * 서비스는 빈 결과를 404로 변환하기만 한다 — 타인 소유 리소스도 동일하게 404를 반환해 존재 여부를 노출하지
 * 않는다.
 */
@Service
public class TodoService {

    private static final Set<String> SORT_WHITELIST = Set.of("createdAt", "dueDate");

    /** 페이지당 최대 건수 (PRD F-13). 서버가 강제해야 하는 값이다 (PRD NF-08). */
    private static final int MAX_PAGE_SIZE = 50;

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final HtmlSanitizer htmlSanitizer;
    private final AttachmentService attachmentService;

    public TodoService(
            TodoRepository todoRepository,
            UserRepository userRepository,
            HtmlSanitizer htmlSanitizer,
            AttachmentService attachmentService) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
        this.htmlSanitizer = htmlSanitizer;
        this.attachmentService = attachmentService;
    }

    @Transactional(readOnly = true)
    public Page<Todo> list(Long userId, Boolean completed, String keyword, Pageable pageable) {
        return todoRepository.search(userId, completed, keyword, sanitizePageable(pageable));
    }

    /**
     * 첨부 수집은 <b>sanitize 이후</b>의 HTML을 대상으로 한다. 순서가 뒤바뀌면 Jsoup이 제거할 태그 안의
     * 첨부까지 LINKED로 승격되어, 본문에 존재하지 않는 파일이 정리 배치에서도 빠진 채 영구 보존된다.
     */
    @Transactional
    public Todo create(Long userId, TodoCreateRequest request) {
        User user = userRepository.getReferenceById(userId);
        String sanitized = htmlSanitizer.sanitize(request.content());
        Todo todo =
                todoRepository.save(
                        Todo.create(
                                user,
                                request.title(),
                                sanitized,
                                request.priority(),
                                request.dueDate()));
        attachmentService.syncTodoAttachments(userId, todo, sanitized);
        return todo;
    }

    @Transactional(readOnly = true)
    public Todo getOwned(Long userId, Long todoId) {
        return todoRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(todoId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** {@link #create}와 같은 이유로 sanitize 이후의 HTML을 첨부 수집에 넘긴다. */
    @Transactional
    public Todo update(Long userId, Long todoId, TodoUpdateRequest request) {
        Todo todo = getOwned(userId, todoId);
        String sanitized = htmlSanitizer.sanitize(request.content());
        todo.updateContent(request.title(), sanitized, request.priority(), request.dueDate());
        attachmentService.syncTodoAttachments(userId, todo, sanitized);
        return todo;
    }

    @Transactional
    public Todo toggle(Long userId, Long todoId, boolean completed) {
        Todo todo = getOwned(userId, todoId);
        if (completed) {
            todo.complete();
        } else {
            todo.uncomplete();
        }
        return todo;
    }

    @Transactional
    public void delete(Long userId, Long todoId) {
        getOwned(userId, todoId).softDelete();
    }

    /**
     * 정렬 화이트리스트 밖 프로퍼티가 들어오면 500 대신 기본 정렬(createdAt,desc)로 조용히 대체하고,
     * 페이지 크기를 {@link #MAX_PAGE_SIZE}로 제한한다.
     *
     * <p>크기 제한이 필요한 이유: 클라이언트가 {@code size=10000}을 보내면 Spring 기본 상한(2000)까지는
     * 그대로 통과해 한 번에 대량 조회가 일어난다. 허용 범위는 서버가 강제한다 (PRD NF-08).
     */
    private Pageable sanitizePageable(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        if (order == null || !SORT_WHITELIST.contains(order.getProperty())) {
            return PageRequest.of(
                    pageable.getPageNumber(), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        // 정렬은 유효하지만 크기가 상한을 넘는 경우도 재구성해야 한다.
        if (size != pageable.getPageSize()) {
            return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
        }
        return pageable;
    }
}
