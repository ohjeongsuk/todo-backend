package com.example.todoapp.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp.domain.Todo;
import com.example.todoapp.domain.User;
import com.example.todoapp.dto.PageResponse;
import com.example.todoapp.dto.TodoCreateRequest;
import com.example.todoapp.dto.TodoResponse;
import com.example.todoapp.dto.TodoUpdateRequest;
import com.example.todoapp.dto.ToggleRequest;
import com.example.todoapp.exception.ApiResponse;
import com.example.todoapp.service.TodoService;

import static org.springframework.data.domain.Sort.Direction.DESC;

/** Todo 목록/생성/단건/수정/토글/삭제 API. */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TodoResponse>> list(
            @AuthenticationPrincipal User principal,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "createdAt", direction = DESC) Pageable pageable) {
        Page<Todo> page = todoService.list(principal.getId(), completed, keyword, pageable);
        return ApiResponse.success(PageResponse.from(page.map(TodoResponse::from)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TodoResponse>> create(
            @AuthenticationPrincipal User principal,
            @Valid @RequestBody TodoCreateRequest request) {
        Todo todo = todoService.create(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(TodoResponse.from(todo)));
    }

    @GetMapping("/{id}")
    public ApiResponse<TodoResponse> get(
            @AuthenticationPrincipal User principal, @PathVariable Long id) {
        return ApiResponse.success(TodoResponse.from(todoService.getOwned(principal.getId(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoResponse> update(
            @AuthenticationPrincipal User principal,
            @PathVariable Long id,
            @Valid @RequestBody TodoUpdateRequest request) {
        return ApiResponse.success(
                TodoResponse.from(todoService.update(principal.getId(), id, request)));
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<TodoResponse> toggle(
            @AuthenticationPrincipal User principal,
            @PathVariable Long id,
            @RequestBody ToggleRequest request) {
        Todo todo = todoService.toggle(principal.getId(), id, request.completed());
        return ApiResponse.success(TodoResponse.from(todo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User principal, @PathVariable Long id) {
        todoService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
