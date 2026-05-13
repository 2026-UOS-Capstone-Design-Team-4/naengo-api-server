package com.naengo.api_server.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    // 회원 탈퇴 익명화 시 NULL — 따라서 nullable. UNIQUE 는 다중 NULL 허용.
    @Column(unique = true, length = 255)
    private String email;

    // 소셜 로그인 사용자는 비밀번호 없음 → nullable
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "USER";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private boolean isBlocked = false;

    // 소셜 로그인 제공자 (LOCAL / KAKAO / GOOGLE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    // 소셜 제공자에서 발급한 사용자 고유 ID
    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    // V3 가 추가하는 컬럼. 탈퇴 익명화 시점에 NOW() 로 채워짐.
    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    public void block() {
        this.isBlocked = true;
    }

    public void unblock() {
        this.isBlocked = false;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 회원 탈퇴 익명화 — PII nullify + 닉네임 꼬리표 + flag 토글 + deleted_at.
     * `users` 행 자체는 보존 (recipes.author_id 정합 유지).
     * 호출 시점에 이미 탈퇴된 경우는 서비스 단에서 미리 거부할 것.
     */
    public void anonymize() {
        this.email = null;
        this.passwordHash = null;
        this.providerId = null;
        this.nickname = "탈퇴한 사용자_" + this.userId;
        this.isBlocked = true;
        this.isActive = false;
        this.deletedAt = ZonedDateTime.now();
    }
}
