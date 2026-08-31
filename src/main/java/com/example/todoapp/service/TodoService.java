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

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final HtmlSanitizer htmlSanitizer;

    public TodoService(
            TodoRepository todoRepository,
            UserRepository userRepository,
            HtmlSanitizer htmlSanitizer) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Transactional(readOnly = true)
    public Page<Todo> list(Long userId, Boolean completed, String keyword, Pageable pageable) {
        return todoRepository.search(userId, completed, keyword, sanitizePageable(pageable));
    }

    @Transactional
    public Todo create(Long userId, TodoCreateRequest request) {
        User user = userRepository.getReferenceById(userId);
        String sanitized = htmlSanitizer.sanitize(request.content());
        return todoRepository.save(
                Todo.create(
                        user, request.title(), sanitized, request.priority(), request.dueDate()));
    }

    @Transactional(readOnly = true)
    public Todo getOwned(Long userId, Long todoId) {
        return todoRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(todoId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public Todo update(Long userId, Long todoId, TodoUpdateRequest request) {
        Todo todo = getOwned(userId, todoId);
        String sanitized = htmlSanitizer.sanitize(request.content());
        todo.updateContent(request.title(), sanitized, request.priority(), request.dueDate());
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

    /** 정렬 화이트리스트 밖 프로퍼티가 들어오면 500 대신 기본 정렬(createdAt,desc)로 조용히 대체한다. */
    private Pageable sanitizePageable(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        if (order == null || !SORT_WHITELIST.contains(order.getProperty())) {
            return PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return pageable;
    }
}
