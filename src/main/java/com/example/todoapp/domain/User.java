package com.example.todoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자 엔티티. 로컬 계정은 {@link #createLocal}, 구글 계정은 {@link #createGoogle}로 생성한다.
 *
 * <p>동일 이메일의 로컬 계정이 이미 있으면 구글 로그인을 거부한다(자동 연동하지 않음, CLAUDE.md 5장).
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** BCrypt 해시. 구글 전용 계정은 비밀번호가 없으므로 null이다 (PRD F-07). */
    @Column(length = 60)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    protected User() {}

    private User(String email, String password, String nickname, AuthProvider provider) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
    }

    public static User createLocal(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, AuthProvider.LOCAL);
    }

    public static User createGoogle(String email, String nickname) {
        return new User(email, null, nickname, AuthProvider.GOOGLE);
    }

    /**
     * 비밀번호를 교체한다 (PRD F-42). 반드시 BCrypt로 인코딩된 값을 넘긴다 (CLAUDE.md 절대 규칙 7).
     *
     * <p>호출한 쪽에서 이 사용자의 모든 Refresh Token을 함께 폐기해야 한다 (PRD F-44).
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public boolean hasPassword() {
        return password != null;
    }
}
