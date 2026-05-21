package com.naengo.api_server.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 사용자(`users`) ↔ 외부 소셜 제공자 계정 link (DBv5 — `user_identities`).
 *
 * <p>옵션 A — DBv5 에 align (V5 의 `social_accounts` 명명을 폐기, AI 팀
 * 003 마이그레이션이 채택한 `user_identities` 사용). PK 컬럼명도 `id`.
 *
 * <p>한 외부 계정(provider + providerUserId)은 우리 사용자 1명에게만 link
 * (UNIQUE(provider, provider_user_id)). DBv5 에는 V5 가 갖고 있던
 * `UNIQUE(user_id, provider)` 제약이 없으므로 그것도 제거.
 *
 * <p>LOCAL 가입자는 row 없음. 탈퇴(users 행 삭제 시) FK CASCADE 로 함께 제거.
 * (단 우리는 탈퇴 시 anonymize + 별도 deleteAllByUserId 로 link 정리.)
 */
@Entity
@Table(name = "user_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** DBv5 신규 — 외부 제공자가 제공한 이메일 (선택). placeholder 또는 동의받은 실 이메일. */
    @Column(length = 255)
    private String email;

    /** "KAKAO" / "GOOGLE" / "NAVER" / "APPLE" — DBv5 CHECK. */
    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
