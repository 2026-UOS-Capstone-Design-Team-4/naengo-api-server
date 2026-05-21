package com.naengo.api_server.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * 회원 메인 테이블 (옵션 A 채택 후, DBv5 정합).
 *
 * <p>V5 까지 우리가 가졌던 {@code email} / {@code deleted_at} / {@code Long} PK /
 * {@code provider}+{@code provider_id} 컬럼은 모두 사라짐. DBv5 의 {@code username}
 * UNIQUE / {@code Integer} PK / {@code updated_at} trigger 에 맞춰 재작성.
 *
 * <p>탈퇴 표식: {@code deletedAt} 컬럼이 없으므로 {@code is_active=false} +
 * nickname 꼬리표만으로 표현. 소셜 link 제거는 서비스 단에서 별도.
 *
 * <p>{@code created_at}/{@code updated_at} 은 DB DEFAULT + trigger 가 관리하나,
 * Hibernate INSERT 시 builder 의 기본값이 들어가도 동일 결과(NOW) 라 무해.
 * UPDATE 시점엔 DB trigger 가 자동으로 NOW 로 override.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    // DBv5: username UNIQUE (nullable). 자체 가입 ID 또는 소셜 placeholder.
    @Column(unique = true, length = 255)
    private String username;

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

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

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
     * 회원 탈퇴 익명화 — PII nullify + 닉네임 꼬리표 + {@code is_active=false}.
     * {@code deleted_at} 컬럼이 없으므로 탈퇴 표식은 {@code is_active=false} 로 단일화.
     * {@code user_identities} link 제거는 서비스 단에서 별도.
     */
    public void anonymize() {
        this.username = null;
        this.passwordHash = null;
        this.nickname = "탈퇴한 사용자_" + this.userId;
        this.isBlocked = true;
        this.isActive = false;
    }

    /** 탈퇴 여부 — deleted_at 부재로 isActive=false 로 단일화. */
    public boolean isWithdrawn() {
        return !this.isActive;
    }
}
