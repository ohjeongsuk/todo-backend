package com.example.domain;

/** 사용자의 가입 경로. 동일 이메일이면 로컬 계정과 구글 계정을 연결한다 (CLAUDE.md 5장). */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
