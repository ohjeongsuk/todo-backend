package com.example.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
 * 할 일 엔티티. {@link #updateContent}는 완료 상태({@code completed}/{@code completedAt})를 건드리지
 * 않는다 — 저장(PUT)이 완료 상태를 덮어쓰지 않아야 한다는 규칙(ROADMAP Phase 4 TODO-10)을 엔티티 단에서
 * 보장한다.
 */
@Entity
@Table(
        name = "todos",
        indexes = @Index(name = "idx_todos_user_deleted", columnList = "user_id, deleted_at"))
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean completed = false;

    private LocalDateTime completedAt;

    protected Todo() {}

    private Todo(User user, String title, String content, Priority priority, LocalDate dueDate) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
    }

    public static Todo create(
            User user, String title, String content, Priority priority, LocalDate dueDate) {
        return new Todo(user, title, content, priority, dueDate);
    }

    public void complete() {
        this.completed = true;
        this.completedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void uncomplete() {
        this.completed = false;
        this.completedAt = null;
    }

    public void updateContent(String title, String content, Priority priority, LocalDate dueDate) {
        this.title = title;
        this.content = content;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
