package com.example.todoapp.dto;

import com.example.todoapp.domain.User;

/** 화면에는 nickname만 노출하고 email은 표시하지 않지만, API 응답 자체에는 둘 다 포함한다 (PRD AUTH-08). */
public record UserResponse(String nickname, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getNickname(), user.getEmail());
    }
}
